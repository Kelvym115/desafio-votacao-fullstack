#!/usr/bin/env python3
"""Carga HTTP opt-in, somente com a biblioteca padrão do Python 3.

Cria uma pauta exclusiva e associados sintéticos; mantém os registros para inspeção.
Exemplo: python3 scripts/performance.py --votes 10000 --workers 20
Os números incluem rede/cliente e descrevem apenas a máquina e a carga executadas.
"""

import argparse
import concurrent.futures
import datetime
import json
import math
import os
import platform
import sys
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080/api/v1")
    parser.add_argument("--votes", type=int, default=1000)
    parser.add_argument("--workers", type=int, default=10)
    args = parser.parse_args()
    if not 1 <= args.votes <= 1_000_000:
        parser.error("--votes deve estar entre 1 e 1000000")
    if not 1 <= args.workers <= 200:
        parser.error("--workers deve estar entre 1 e 200")
    if not args.base_url.startswith(("http://", "https://")):
        parser.error("--base-url deve usar http:// ou https://")
    args.base_url = args.base_url.rstrip("/")
    return args


def request(base_url, method, path, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(base_url + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
        return 0, str(error)


def require(response, expected):
    status, body = response
    if status != expected:
        raise RuntimeError(f"Esperado HTTP {expected}, recebido {status}: {body}")
    return body


def percentile(sorted_values, fraction):
    return sorted_values[max(0, math.ceil(len(sorted_values) * fraction) - 1)]


def main():
    args = arguments()
    run_id = uuid.uuid4().hex[:12]
    pauta = require(request(args.base_url, "POST", "/pautas", {
        "titulo": f"Carga sintética {run_id}",
        "descricao": f"{args.votes} votos solicitados; {args.workers} workers HTTP."
    }), 201)
    path = f"/pautas/{pauta['id']}"
    require(request(args.base_url, "POST", path + "/sessoes", {"duracaoMinutos": 1440}), 201)
    print(f"Pauta {pauta['id']}: enviando {args.votes} votos com {args.workers} workers...", file=sys.stderr)

    def vote(index):
        start = time.perf_counter()
        status, body = request(args.base_url, "POST", path + "/votos", {
            "associadoId": f"carga-{run_id}-{index}",
            "voto": "SIM" if index % 2 == 0 else "NAO"
        })
        return status, (time.perf_counter() - start) * 1000, body

    latencies = []
    statuses = Counter()
    samples = []
    started = time.perf_counter()
    indices = iter(range(args.votes))
    # Janela limitada evita criar centenas de milhares de Future simultaneamente.
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        pending = {executor.submit(vote, i) for i in range(min(args.workers * 2, args.votes))}
        for _ in range(len(pending)):
            next(indices)
        while pending:
            done, pending = concurrent.futures.wait(pending, return_when=concurrent.futures.FIRST_COMPLETED)
            for future in done:
                status, elapsed, body = future.result()
                statuses[status] += 1
                latencies.append(elapsed)
                if status != 201 and len(samples) < 5:
                    samples.append({"http_status": status, "resposta": body})
                index = next(indices, None)
                if index is not None:
                    pending.add(executor.submit(vote, index))
    duration = time.perf_counter() - started
    latencies.sort()
    result_started = time.perf_counter()
    result = require(request(args.base_url, "GET", path + "/resultado"), 200)
    result_ms = (time.perf_counter() - result_started) * 1000
    consistent = result["total"] == statuses[201]
    if statuses[201] == args.votes:
        consistent = consistent and result["sim"] == (args.votes + 1) // 2 and result["nao"] == args.votes // 2
    report = {
        "medido_em_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "cliente": {"python": platform.python_version(), "sistema": platform.platform(),
                    "cpus_logicas": os.cpu_count()},
        "pauta_id": pauta["id"], "base_url": args.base_url,
        "votos_solicitados": args.votes, "workers": args.workers,
        "duracao_segundos": round(duration, 3),
        "requisicoes_por_segundo": round(args.votes / duration, 2),
        "votos_aceitos_por_segundo": round(statuses[201] / duration, 2),
        "http_status": dict(statuses),
        "latencia_ms": {"min": round(latencies[0], 2),
                        "p50": round(percentile(latencies, 0.50), 2),
                        "p95": round(percentile(latencies, 0.95), 2),
                        "p99": round(percentile(latencies, 0.99), 2),
                        "max": round(latencies[-1], 2)},
        "consulta_resultado_ms": round(result_ms, 2),
        "resultado": result, "persistencia_coerente": consistent,
        "amostras_de_erros": samples,
        "limite_da_medicao": "Inclui overhead do cliente Python e HTTP; não é garantia de capacidade de produção."
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if consistent and statuses[201] == args.votes else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print(f"Falha na preparação ou conferência: {error}", file=sys.stderr)
        sys.exit(1)
