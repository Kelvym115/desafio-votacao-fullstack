# Pauta — votação em assembleias

Aplicação fullstack para cadastrar pautas, abrir sessões com prazo, receber um voto por associado em cada pauta e acompanhar o resultado. Backend Java/Spring Boot e interface React/TypeScript.

Solução do [desafio original](docs/enunciado.md), com testes unitários, integração HTTP/banco e testes no navegador. Consulte a [conferência dos requisitos](docs/entrega.md) e as [evidências de validação](docs/validacao.md).

**Aplicação publicada:** [pautas.holomind.dev](https://pautas.holomind.dev) · [Swagger UI](https://pautas.holomind.dev/swagger-ui/index.html) · [Pull request da entrega](https://github.com/somosdb/desafio-votacao-fullstack/pull/63).

A demonstração usa PostgreSQL persistente, HTTPS e uma VM dedicada; continua disponível com o computador do desenvolvedor desligado. Os dados identificados como smoke/assembleia de teste foram criados pelas verificações automatizadas. É possível cadastrar uma nova pauta para avaliar o fluxo.

## Executar localmente

Pré-requisitos: **JDK 17 ou superior**, **Node.js 24 LTS** (ou 22.12+) e acesso à internet na primeira execução para baixar dependências. Não é necessário instalar Maven, Docker ou um servidor de banco para este modo.

Clone o fork e selecione a branch da solução:

```bash
git clone --branch codex/desafio-votacao-fullstack https://github.com/Kelvym115/desafio-votacao-fullstack.git
cd desafio-votacao-fullstack
```

Na pasta deste README:

```bash
./dev.sh
```

Abra **http://localhost:5173**. O script instala dependências com o lockfile, compila o backend e inicia os dois processos. `Ctrl+C` encerra os processos e preserva o banco. Se usar nvm, `nvm install && nvm use` seleciona a versão indicada no `.nvmrc`; o script também reconhece Node 24/22 compatível já instalado pelo nvm.

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health
- Log local do backend: `.run/backend.log`

O H2 grava os dados em **`backend/data/`**. Encerrar e iniciar novamente a aplicação não apaga pautas, sessões ou votos. O banco não depende do navegador. Remover essa pasta apaga os dados locais; não faça isso para apenas reiniciar a aplicação.

Portas ocupadas são reportadas pelo script, que não encerra processos existentes. Para uso individual pela IDE, execute `./mvnw spring-boot:run` na pasta `backend` e `npm run dev` na pasta `frontend`.

## Roteiro de teste manual

1. Cadastre uma pauta com título e descrição opcional.
2. Selecione a pauta e abra uma sessão. Sem duração personalizada, ela dura **1 minuto**.
3. Informe uma identificação de associado, escolha Sim ou Não e confirme.
4. Tente votar de novo com o mesmo associado: a aplicação deve impedir a repetição.
5. Use outra identificação e confira a contagem de votos.
6. Aguarde o prazo: a sessão encerra e o resultado passa de parcial para final.
7. Reinicie a aplicação e confira que os dados continuam disponíveis.

Para o fluxo principal, deixe a simulação de CPF desativada. Quando um CPF é informado, o cliente fake pode rejeitar o voto aleatoriamente, conforme o bônus do enunciado; isso não representa uma consulta real a qualquer órgão.

## Testes automatizados

```bash
# Unitários + integração do backend, testes do frontend e build TypeScript/produção
./test.sh

# Inclui teste de ponta a ponta com navegador Chromium e API reais
./test.sh --e2e
```

O primeiro teste de navegador baixa o Chromium. Seus dados ficam separados do banco local de uso manual. O teste de persistência usa seu próprio arquivo temporário e reinicia o contexto da aplicação sobre o mesmo banco.

Para testar uma implantação já iniciada, execute na pasta `frontend`: `E2E_BASE_URL=https://pautas.holomind.dev npm run test:e2e`. Nesse modo, o Playwright usa o serviço informado e cria dados sintéticos nele; não inicia os servidores locais de teste.

Comandos individuais:

```bash
cd backend
./mvnw test       # Unitários
./mvnw verify     # Unitários + integração + relatório de cobertura

cd ../frontend
npm ci
npm test
npm run build
```

Relatórios: `backend/target/surefire-reports/`, `backend/target/failsafe-reports/` e `backend/target/site/jacoco/index.html`. A evidência da execução feita neste projeto fica em [docs/validacao.md](docs/validacao.md).

Para reproduzir a medição de carga, execute o script contra uma instância dedicada a testes:

```bash
python3 scripts/performance.py --base-url http://localhost:8080/api/v1 --votes 100000 --workers 20
```

O script cria uma pauta própria com associados sintéticos e mantém esses dados para inspeção. Ele confirma a contagem persistida e devolve erro se alguma requisição falhar. O [relatório de carga local](docs/performance-local.json) registra uma execução real, com suas limitações descritas na validação.

## Regras e API

O [contrato documentado](docs/contrato.md) contém entradas, saídas, limites e erros. A API é versionada pelo caminho `/api/v1`.

```bash
curl -i http://localhost:8080/api/v1/pautas \
  -H 'Content-Type: application/json' \
  -d '{"titulo":"Novo horário da assembleia","descricao":"Votação de exemplo"}'

# Troque 1 pelo id devolvido na criação.
curl -i http://localhost:8080/api/v1/pautas/1/sessoes \
  -H 'Content-Type: application/json' -d '{"duracaoMinutos":5}'

curl -i http://localhost:8080/api/v1/pautas/1/votos \
  -H 'Content-Type: application/json' \
  -d '{"associadoId":"associado-exemplo","voto":"SIM"}'

curl http://localhost:8080/api/v1/pautas/1/resultado
```

Autenticação é dispensada pelo enunciado. A identificação do associado é fornecida pelo cliente; não há comprovação de identidade. Isso é uma simplificação do exercício e precisa ser revisto antes de um uso real.

## Docker e PostgreSQL

Também há uma opção que empacota a interface junto do backend e usa PostgreSQL:

```bash
docker compose up --build
```

Nesse modo, a aplicação e o Swagger ficam em **http://localhost:8080**, e o volume `postgres-data` mantém os dados. `docker compose down` preserva o volume; a opção `-v` remove seus dados. A senha de exemplo no compose é exclusiva para desenvolvimento; use configuração própria fora da máquina local.

O perfil `postgres` aceita `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. Para hospedar a imagem, configure essas variáveis, `SPRING_PROFILES_ACTIVE=postgres` e um PostgreSQL persistente. A aplicação aceita a porta por `PORT` (padrão 8080), serve a interface na raiz e expõe health em `/actuator/health`. Credenciais devem ser configuradas no serviço de hospedagem, sem incluí-las no Git.

O [workflow de CI](.github/workflows/ci.yml) verifica testes com H2, testes com PostgreSQL 17, navegador e execução da imagem Docker. O estágio Docker também reinicia a aplicação e confere a persistência dos dados. O ambiente e as verificações efetivamente concluídas estão no [registro de validação](docs/validacao.md); a situação da hospedagem está na [conferência da entrega](docs/entrega.md).

Para a implantação com domínio e HTTPS, consulte o [guia de hospedagem](deploy/README.md). O compose de produção separa o proxy Caddy, a aplicação e o banco; a imagem é verificada em AMD64 e ARM64. O guia inclui atualização, backup e custos da infraestrutura.

## Organização e decisões

```text
backend/       API, migrações de banco e testes Java
frontend/      Interface React, testes de componentes e de navegador
scripts/       Execução compartilhada e teste de desempenho
docs/          Contrato, decisões e evidências de validação
deploy/        Implantação em VM, HTTPS e infraestrutura AWS opcional
dev.sh         Inicia a aplicação local
test.sh        Executa testes e build
```

[Decisões e arquitetura](docs/arquitetura.md) explica a organização, os cuidados com concorrência/tempo, o uso de H2 e PostgreSQL e as limitações conhecidas. A licença MIT do repositório de origem foi preservada em [LICENSE](LICENSE).
