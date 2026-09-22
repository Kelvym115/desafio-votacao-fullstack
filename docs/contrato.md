# Contrato e decisões da implementação

Java 17, Spring Boot 3.5.16, React com TypeScript. H2 em arquivo para execução local sem instalar banco; perfil PostgreSQL para Docker. API REST sob `/api/v1`. Datas em UTC/ISO-8601, apresentação no fuso do navegador. IDs numéricos inteiros positivos. Sem autenticação, conforme enunciado.

## Regras adotadas

- Uma sessão por pauta, sem reabertura. Abrir novamente retorna 409.
- Duração em minutos inteiros entre 1 e 1440; omissão ou `null` usam 1 minuto.
- Voto aceito quando a sessão existe e `agora < encerraEm`. Exatamente no prazo já está encerrada. Horário do servidor é a referência.
- Um voto imutável por par pauta/associado, garantido também por restrição única no banco.
- Associado é uma identificação textual, de 1 a 64 caracteres após remover espaços nas extremidades. Não é necessário cadastro ou login.
- Apenas `SIM` e `NAO` são valores válidos no JSON. A interface exibe Sim/Não.
- Resultado parcial disponível durante a sessão. Sem sessão: AGUARDANDO. Aberta: EM_ANDAMENTO. Encerrada: APROVADA se sim > nao, REJEITADA se nao > sim, EMPATE se iguais e total > 0, SEM_VOTOS se total = 0.
- Pautas, sessões e votos persistem. Nenhum job em memória é necessário para encerrar sessões: o estado é calculado pelas datas persistidas.
- Integração CPF é um bônus fake, opcional por voto via campo `cpf`; sem CPF informado o voto segue a regra principal. Um CPF de 11 dígitos recebe aleatoriamente APTO, INAPTO ou INVALIDO; outro formato é inválido. Não é uma consulta real de CPF. O cliente deve ser substituível nos testes.

## Recursos

### Pauta

```json
{"id":1,"titulo":"Novo horário","descricao":"Descrição opcional","criadaEm":"2026-09-22T12:00:00Z","sessao":null}
```

Quando há sessão, `sessao` é `{ "id": 1, "abertaEm": "...", "encerraEm": "...", "status": "ABERTA" }` (ou `ENCERRADA`).

### Operações

| Método / rota | Entrada | Saída |
| --- | --- | --- |
| POST /pautas | `{titulo, descricao?}` título obrigatório até 140, descrição até 2000 | 201 Pauta |
| GET /pautas?page=0&size=12&busca=texto | página >= 0, tamanho 1–100, busca opcional por título | 200 `{content: Pauta[], page, size, totalElements, totalPages}` ordenado por criadaEm desc e id desc |
| GET /pautas/{id} | — | 200 Pauta |
| POST /pautas/{id}/sessoes | `{duracaoMinutos?: number}` (corpo opcional) | 201 Sessao |
| POST /pautas/{id}/votos | `{associadoId: string, voto: "SIM" ou "NAO", cpf?: string}` | 201 `{id, pautaId, associadoId, voto, registradoEm}` |
| GET /pautas/{id}/resultado | — | 200 `{pautaId, sim, nao, total, status, resultado}` |
| GET /associados/{cpf}/elegibilidade | CPF de teste | 200 `{status:"ABLE_TO_VOTE"}`, 404 `{status:"UNABLE_TO_VOTE"}` para INAPTO ou ProblemDetail 404 para INVALIDO |

Pauta ausente: 404. Dados inválidos/JSON inválido: 400. Sessão não aberta, encerrada, já existente ou voto repetido: 409. CPF inválido/inapto no voto: 404, documentando a escolha para a ambiguidade do enunciado.

Em Resultado, `status` é `NAO_INICIADA`, `ABERTA` ou `ENCERRADA`; `resultado` é `AGUARDANDO`, `EM_ANDAMENTO`, `APROVADA`, `REJEITADA`, `EMPATE` ou `SEM_VOTOS`.

Erros no formato ProblemDetail: `{type, title, status, detail, instance, code, errors?}`; `errors`, quando presente, é um mapa de campo para mensagem. Mensagens em português, sem stack trace ou dados internos.

## Execução e documentação

- Backend local: porta 8080, health em `/actuator/health`, OpenAPI em `/v3/api-docs`, Swagger UI em `/swagger-ui/index.html`.
- Frontend: porta 5173, proxy `/api` para backend; caminho da API configurável na versão empacotada.
- Testes: JUnit 5/Mockito, integração Spring Boot com banco real, incluindo concorrência e persistência após reinício. A CI executa a API com H2 e PostgreSQL; o teste de reinício em arquivo usa H2 e o smoke Docker confere PostgreSQL. Frontend com Vitest/Testing Library e fluxo integrado no navegador.
- Situação da publicação e evidências: [entrega](entrega.md) e [validação](validacao.md).
