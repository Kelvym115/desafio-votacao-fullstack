# Registro de validação

Validação local executada em **2026-09-22**, em macOS ARM, Java 17.0.16 e Node 24.11.1. O histórico abaixo separa as execuções locais das verificações posteriores de entrega.

## Resultado dos testes

| Verificação | Resultado observado |
| --- | --- |
| Maven `verify`: testes isolados (unitários e MVC standalone) | 45 passaram; zero falhas/erros/ignorados |
| Maven `verify`: integração | 46 passaram; zero falhas/erros/ignorados |
| Vitest | 21 passaram em 3 arquivos |
| Playwright + API real | 4 passaram: 2 cenários em desktop e 2 em celular emulado |
| TypeScript + Vite produção | Build concluído |
| Prettier | Verificação de formatação concluída |
| Execução local com `./dev.sh` | Backend e Vite iniciados; health UP; frontend HTTP 200; proxy da API funcionando; 6 caminhos OpenAPI disponíveis |
| Shell | Sintaxe dos scripts conferida; seleção do Java e Node locais verificada; JAVA_HOME respeitado na compilação e execução |

**116 execuções de testes passaram.** Parâmetros de testes Java são contados separadamente pelos relatórios do Maven. Os 4 testes de navegador executam os mesmos 2 cenários nos dois tamanhos de tela.

Comandos usados: Maven Wrapper com `verify` e repositório local de dependências; `npm test`; `npm run build`; `npm run format:check`; `npm run test:e2e`. Os scripts `./test.sh` e `./test.sh --e2e` reúnem essas verificações para reprodução.

O relatório JaCoCo agregado de unitários e integração apresentou **243/250 linhas (97,2%)** e **70/76 ramificações (92,1%)**. Cobertura indica trechos executados, não garante ausência de bugs. Relatórios completos são gerados em `backend/target/site/jacoco/`.

A aplicação de uso manual iniciou com a lista de pautas vazia. Os 100 mil votos de carga e os dados de testes não foram copiados para esse banco. Cache, relatórios e banco local têm regras de exclusão no `.gitignore` e não integram a entrega.

## O que foi demonstrado

- Criação de pauta, consulta, ordenação, paginação e busca.
- Sessão com duração padrão, personalizada e limites de entrada.
- Voto Sim/Não e validação de JSON, campos, enum e tipos.
- Mesmo associado em pautas diferentes; segundo voto na mesma pauta rejeitado.
- Requisições concorrentes disputando voto e abertura da mesma sessão, com integridade preservada.
- Voto antes do encerramento aceito, voto no instante exato recusado; relógio controlado nos testes.
- Resultado parcial, aprovação, rejeição, empate e sessão sem votos.
- CPF fake com respostas determinísticas de apto, inapto e inválido durante os testes.
- Reinício efetivo do contexto/servidor sobre o mesmo arquivo H2: pauta, sessão, voto e horário final preservados.
- Fluxo real no navegador: criar pauta, abrir sessão, votar, receber 409 para repetição, votar com outro associado, conferir 50%/50% e recarregar a página.
- Busca sem resultado, limpeza da busca, Escape no diálogo, restauração do foco e ausência de rolagem horizontal indevida em desktop e celular.

Os testes identificaram e ajudaram a corrigir a precedência do handler de erro HTTP e a restauração de foco do diálogo. Os resultados acima correspondem às execuções após as correções.

## Desempenho local

Execução com H2 em arquivo separado do banco de uso manual, Java 17.0.16, 8 CPUs lógicas, cliente Python 3.9.6 e **20 workers**. Houve aquecimento anterior com 200 votos. Cliente e servidor rodaram na mesma máquina, junto de outras atividades de desenvolvimento.

| Métrica | Valor observado |
| --- | --- |
| Requisições de voto | 100.000 |
| Respostas 201 | 100.000 |
| Erros | 0 |
| Contagem persistida | 50.000 Sim + 50.000 Não |
| Tempo | 39,9 segundos |
| Vazão | 2.506,25 votos/s |
| Latência p50 / p95 / p99 | 6,30 / 17,95 / 32,17 ms |
| Consulta única do resultado ao final da carga | 116,81 ms |

Fonte reproduzível: [relatório JSON](performance-local.json) e [script](../scripts/performance.py). A pauta permaneceu aberta durante a carga; o resultado medido é parcial. A execução foi realizada na porta 18081, em banco de teste, e essa instância foi encerrada.

Esses números incluem overhead do cliente Python e da comunicação HTTP. Não são garantia de capacidade em produção, teste de longa duração ou medição de PostgreSQL. O script aceita outros volumes para avaliação no ambiente final.

## Revisão para entrega em 2026-09-22

A revisão identificou que o relógio adiantado do navegador podia esconder o formulário mesmo com a sessão aberta no backend. A interface passou a confiar no status da API e a apresentar a contagem regressiva como estimativa. Dois testes adicionais cobrem relógios adiantado e atrasado.

Depois da correção, `./test.sh --e2e` foi executado novamente e concluiu com **118 testes aprovados**: 45 isolados Java, 46 de integração, 23 do frontend e 4 de navegador. A mesma execução concluiu a formatação e os builds Java/TypeScript/Vite.

O workflow de CI foi acrescentado para exercitar Linux, PostgreSQL 17 e a imagem completa. A situação e os links da publicação são mantidos em [entrega.md](entrega.md).

## Execução remota no GitHub Actions

A [execução 35799815102](https://github.com/Kelvym115/desafio-votacao-fullstack/actions/runs/35799815102), sobre o commit `effbb332468f01d0699858a626d4834914c8f797`, concluiu os **três jobs com sucesso** em 2026-09-22:

| Ambiente | Evidência |
| --- | --- |
| Ubuntu, Java 17 e Node 24 | 45 testes isolados Java + 46 de integração H2 + 23 frontend + 4 navegador; formatação e builds aprovados |
| PostgreSQL 17 | 45 testes isolados e 46 de integração aprovados; a suíte HTTP (45 casos) usa PostgreSQL e o teste específico de reinício em arquivo continua usando H2 |
| Docker com frontend empacotado e PostgreSQL | Imagem construída, health UP, HTML e módulo React servidos, criação de pauta/sessão, votos e duplicidade conferidos |
| Recriação dos containers | `docker compose down` seguido de `up` preservou o volume; mesmas pauta, sessão, datas e contagens recuperadas; voto duplicado continuou retornando 409 |

Os logs confirmaram PostgreSQL **17.11** na imagem usada. Os relatórios JUnit/JaCoCo e Playwright são anexados como artefatos da CI com retenção de 7 dias; os testes podem ser reproduzidos pelo workflow. Isso comprova execução em Linux/PostgreSQL/Docker, mas não uma hospedagem pública permanente.

## Publicação e validação na nuvem em 2026-09-22

Aplicação disponível em **[https://pautas.holomind.dev](https://pautas.holomind.dev)**, com [Swagger UI](https://pautas.holomind.dev/swagger-ui/index.html). O ambiente usa Ubuntu 24.04 ARM64, Docker Compose, Java 17, PostgreSQL 17 e Caddy numa VM EC2 `t4g.small`. O DNS e o proxy da Cloudflare usam HTTPS estrito até a origem. O site independe do computador de desenvolvimento.

A [execução 35801812696 da CI](https://github.com/Kelvym115/desafio-votacao-fullstack/actions/runs/35801812696), sobre o commit `ed13a7e328280576d87f47c5ddf356bd4faf976c` usado no servidor, aprovou os **quatro jobs**: Java/React/navegador, integração PostgreSQL, Docker AMD64 e Docker ARM64. Ambos os jobs Docker verificaram o compose local e o compose de produção com Caddy, incluindo recriação dos containers e preservação dos votos. O runtime Temurin 17 Jammy substitui a imagem Alpine sem suporte ARM64 nessa versão.

| Verificação pública | Resultado observado |
| --- | --- |
| HTTPS da origem | Certificado emitido pelo Let's Encrypt; conexão verificada sem desabilitar validação TLS |
| HTTPS pelo proxy Cloudflare | HTTP 200 e health `UP`; API com `Cache-Control: no-store` e `cf-cache-status: DYNAMIC` |
| Swagger e OpenAPI | HTTP 200; 6 caminhos de API e URL de servidor `https://pautas.holomind.dev` |
| Smoke sobre o domínio público | Interface, criação de pauta/sessão, votos Sim/Não, total 2 e repetição recusada com 409 |
| Reinício completo da VM | Docker e os três containers voltaram automaticamente; smoke recuperou a mesma pauta, sessão, datas e contagens; repetição permaneceu bloqueada |
| Playwright contra a implantação | **4 testes passaram** em 14,4 s: fluxos completos, recarga, busca, foco e layout em desktop e Pixel 7 emulado |
| Recursos após reinício | Containers de aplicação e banco saudáveis; aproximadamente 1,0 GiB de memória disponível, sem swap, na leitura feita após o smoke |

Durante a publicação, o resolvedor do macOS manteve uma resposta negativa para o novo domínio. As primeiras execuções de navegador falharam por DNS. Cloudflare (1.1.1.1), Google (8.8.8.8), o resolvedor de rede e a VM já resolviam o registro. Para a execução aprovada, somente os processos de teste resolveram o hostname para o IP público confirmado da Cloudflare; domínio, HTTPS, API e banco permaneceram reais, com validação do certificado. Nenhuma configuração permanente de DNS foi alterada. Esse ajuste temporário ficou fora do código publicado.

As pautas sintéticas criadas pelos testes foram mantidas para inspeção. A carga de 100 mil votos não foi executada na VM pública; os números de desempenho acima continuam exclusivos do ambiente local.

## Limites da validação e hospedagem

- O Docker não estava disponível na máquina macOS. A validação local de banco foi com H2 real, em memória para parte dos testes e em arquivo para reinício/carga. Docker e PostgreSQL são verificados separadamente na CI; uma configuração de CI, por si só, não comprova sua execução bem-sucedida.
- A demonstração usa uma única VM, sem alta disponibilidade. O backup feito antes das atualizações fica no mesmo disco; sua exportação é necessária para proteger contra perda do servidor.
- A oferta de processamento T4g tem prazo e franquia. Disco/IP têm cobrança separada; custos e encerramento estão no [guia de hospedagem](../deploy/README.md).
- O navegador usado foi Chromium, em desktop e emulação Pixel 7. Outros motores e aparelhos físicos não foram testados.
- A autorização é dispensada no exercício; identidade de associado fornecida pelo cliente e o CPF fake não atendem a um cenário de produção com identidade verificada.
