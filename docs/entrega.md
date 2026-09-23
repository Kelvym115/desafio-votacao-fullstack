# Conferência dos requisitos

Revisão em 2026-09-22, a partir do [enunciado preservado](enunciado.md) e das orientações adicionais do processo: React, testes unitários e integrados obrigatórios, fork e pull request.

| Pedido | Implementação / evidência |
| --- | --- |
| Java com Spring Boot | `backend/`, Java 17 e Spring Boot 3.5.16 |
| Interface React consumindo os endpoints | `frontend/`, cadastro, sessão, votação e apuração; layout responsivo |
| Cadastrar pauta | `POST /api/v1/pautas`, validado por testes HTTP e navegador |
| Duração informada ou padrão de 1 minuto | `POST /api/v1/pautas/{id}/sessoes`; testes de duração, limites e omissão |
| Sim/Não, associado com ID, um voto por pauta | DTO validado e restrição única `(pauta_id, associado_id)`; testes de repetição e concorrência |
| Contabilizar e mostrar resultado | Agregação SQL, resultado parcial/final, empate e ausência de votos |
| Persistir após reinício | H2 em arquivo no modo local; PostgreSQL em volume no Docker; teste de reinício |
| Testes unitários e integrados | JUnit/Mockito, HTTP + Spring + banco, Vitest/Testing Library e Playwright |
| Instruções de execução | README, Maven Wrapper, lockfile npm, `dev.sh`, `test.sh`, Docker Compose |
| Erros, logs e documentação de API | ProblemDetail, logs sem payload pessoal, OpenAPI e Swagger UI |
| Explicação das escolhas | [Arquitetura](arquitetura.md) e [contrato](contrato.md) |
| Fork e PR | [Fork](https://github.com/Kelvym115/desafio-votacao-fullstack/tree/codex/desafio-votacao-fullstack) e [PR #63 aberto](https://github.com/somosdb/desafio-votacao-fullstack/pull/63) para `somosdb/main` |
| Executar na nuvem | **Pendente de definição e conexão do ambiente de hospedagem**. CI não equivale a uma URL pública de avaliação. |

Os testes executados, os números e as limitações constam no [registro de validação](validacao.md). Não há serviço de autenticação, conforme a dispensa no enunciado. Uma única sessão por pauta e a duração em minutos inteiros são decisões explícitas, pois esses detalhes não foram especificados.

## Bônus

- Cliente fake de CPF substituível, aleatório na aplicação e controlado nos testes. A consulta é opcional por voto; formato de 11 dígitos não é validação real de CPF. A interpretação dos retornos 404 está no contrato.
- API versionada em `/api/v1`, com estratégia de evolução documentada.
- Consulta agregada, paginação, índices e [medição local de 100 mil votos](performance-local.json), sem extrapolar o resultado para produção/PostgreSQL.

## Verificação adicional da entrega

- O status de sessão do servidor controla a votação na interface. O contador do navegador é apenas uma estimativa, para evitar que relógios adiantados bloqueiem uma votação válida.
- A [CI passou nos três jobs](https://github.com/Kelvym115/desafio-votacao-fullstack/actions/runs/35799815102): Linux/Java/React/navegador, integração com PostgreSQL e imagem Docker com reinício preservando os dados.
- Apenas código e documentação técnica são publicados. Bancos locais, credenciais, caches e anotações do processo seletivo ficam fora do repositório.
