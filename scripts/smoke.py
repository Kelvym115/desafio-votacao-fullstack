#!/usr/bin/env python3
"""Smoke HTTP em ambiente de teste; cria dados sintéticos e verifica reinício.

    python3 scripts/smoke.py create --state-file /tmp/votacao-smoke.json
    docker compose down && docker compose up -d
    python3 scripts/smoke.py verify --state-file /tmp/votacao-smoke.json

O arquivo de estado guarda somente IDs/dados sintéticos, nunca credenciais.
O modo verify não cria outra pauta: consulta a mesma e tenta repetir um voto.
"""

import argparse
import json
import sys
import time
import uuid
from html.parser import HTMLParser
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def request(url, expected=200, body=None):
    payload = None if body is None else json.dumps(body).encode("utf-8")
    headers = {} if body is None else {"Content-Type": "application/json"}
    try:
        response = urlopen(Request(url, data=payload, headers=headers), timeout=10)
    except HTTPError as error:
        response = error
    with response:
        content = response.read()
        require(response.status == expected,
                f"{url}: HTTP {response.status}; esperado {expected}.")
        return content, response.headers


def json_request(url, expected=200, body=None):
    content, _ = request(url, expected, body)
    return json.loads(content)


def wait_for_health(base_url, timeout):
    deadline = time.monotonic() + timeout
    while True:
        try:
            if json_request(base_url + "/actuator/health").get("status") == "UP":
                return
        except (URLError, TimeoutError, RuntimeError, ValueError, OSError):
            pass
        require(time.monotonic() < deadline,
                f"Health não ficou UP dentro de {timeout} segundos em {base_url}.")
        time.sleep(1)


class FrontendHtml(HTMLParser):
    def __init__(self):
        super().__init__()
        self.has_root = False
        self.modules = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get("id") == "root":
            self.has_root = True
        if tag == "script" and attrs.get("type") == "module" and attrs.get("src"):
            self.modules.append(attrs["src"])


def verify_frontend(frontend_url):
    content, headers = request(frontend_url + "/")
    require("text/html" in headers.get("Content-Type", ""), "A raiz não serviu HTML.")
    html = FrontendHtml()
    html.feed(content.decode("utf-8"))
    require(html.has_root and html.modules, "HTML não contém a raiz React e seu módulo.")
    origin = urlsplit(frontend_url)
    for module in html.modules:
        module_url = urljoin(frontend_url + "/", module)
        parsed = urlsplit(module_url)
        require((parsed.scheme, parsed.netloc) == (origin.scheme, origin.netloc),
                "O módulo React deve ser servido pela mesma origem.")
        javascript, headers = request(module_url)
        require(bool(javascript) and "javascript" in headers.get("Content-Type", ""),
                "O módulo da interface não foi servido como JavaScript.")


def verify(api, state):
    pauta_id = state["pauta"]["id"]
    pauta = json_request(f"{api}/pautas/{pauta_id}")
    for field in ("id", "titulo", "descricao", "criadaEm"):
        require(pauta[field] == state["pauta"][field], f"Pauta não preservou {field}.")
    require(pauta.get("sessao") is not None, "Sessão não persistiu.")
    for field in ("id", "abertaEm", "encerraEm"):
        require(pauta["sessao"][field] == state["sessao"][field],
                f"Sessão não preservou {field}.")
    duplicate = json_request(f"{api}/pautas/{pauta_id}/votos", 409,
                             {"associadoId": state["associado"], "voto": "NAO"})
    require(duplicate.get("code") == "VOTO_DUPLICADO",
            "O conflito deveria identificar o voto duplicado.")
    result = json_request(f"{api}/pautas/{pauta_id}/resultado")
    expected = {"pautaId": pauta_id, "sim": 1, "nao": 1, "total": 2,
                "status": "ABERTA", "resultado": "EM_ANDAMENTO"}
    require(all(result.get(key) == value for key, value in expected.items()),
            f"Resultado incorreto: {result}.")


def create(api, state_file):
    require(not state_file.exists(), "Arquivo de estado já existe; use verify ou outro arquivo.")
    identifier = uuid.uuid4().hex
    pauta = json_request(api + "/pautas", 201,
                         {"titulo": f"Smoke CI {identifier}",
                          "descricao": "Dados sintéticos para verificar execução e persistência."})
    pauta_url = f"{api}/pautas/{pauta['id']}"
    # Uma hora evita confundir expiração da sessão com perda de persistência no CI.
    sessao = json_request(pauta_url + "/sessoes", 201, {"duracaoMinutos": 60})
    associado = f"smoke-{identifier}"
    json_request(pauta_url + "/votos", 201, {"associadoId": associado, "voto": "SIM"})
    json_request(pauta_url + "/votos", 201,
                 {"associadoId": associado + "-outro", "voto": "NAO"})
    state = {"pauta": pauta, "sessao": sessao, "associado": associado}
    verify(api, state)
    state_file.parent.mkdir(parents=True, exist_ok=True)
    with state_file.open("x", encoding="utf-8") as output:
        json.dump(state, output, ensure_ascii=False, indent=2)
        output.write("\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("create", "verify"))
    parser.add_argument("--base-url", default="http://127.0.0.1:8080",
                        help="Origem da aplicação, sem /api/v1.")
    parser.add_argument("--frontend-url", help="Opcional: origem Vite para verificação local.")
    parser.add_argument("--state-file", type=Path, required=True)
    parser.add_argument("--health-timeout", type=int, default=120)
    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")
    try:
        wait_for_health(base_url, args.health_timeout)
        verify_frontend((args.frontend_url or base_url).rstrip("/"))
        if args.mode == "create":
            create(base_url + "/api/v1", args.state_file)
        else:
            with args.state_file.open(encoding="utf-8") as source:
                verify(base_url + "/api/v1", json.load(source))
    except (URLError, TimeoutError, RuntimeError, ValueError, OSError, KeyError) as error:
        print(f"Smoke falhou: {error}", file=sys.stderr)
        return 1
    print(f"Smoke {args.mode}: health, interface, pauta, sessão, contagem e voto único conferidos.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
