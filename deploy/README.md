# Hospedagem com domínio próprio

Configuração para uma VM dedicada com Ubuntu 24.04, Docker Compose, 2 GB de RAM e um domínio apontando para o servidor. React e API são servidos pelo mesmo backend; PostgreSQL mantém os dados em volume persistente. Caddy termina HTTPS e renova o certificado automaticamente.

## Preparar

1. Criar uma VM dedicada. A criação de recursos pagos exige autorização do responsável pela conta. A configuração não depende de um provedor específico.
2. Configurar um registro DNS `A` do subdomínio para um IP estável da VM. Não substituir o domínio principal ou registros usados por outros serviços.
3. Liberar TCP 80 e 443. Restringir SSH (22) ao IP do administrador. Não liberar 5432 ou 8080.
4. Clonar o repositório e selecionar o commit que passou pela CI.
5. Executar na raiz do repositório, dentro da VM:

```bash
sudo ./deploy/install-docker-ubuntu.sh
sudo ./deploy/start.sh votacao.seu-dominio.com
```

O instalador configura o repositório APT oficial do Docker. O segundo comando gera uma senha aleatória apenas na primeira execução e salva a configuração em `/opt/votacao-state/.env`, com acesso somente ao root. Se o volume PostgreSQL já existir e esse arquivo estiver ausente, o script interrompe o deploy para que a configuração original seja restaurada; não gera outra senha para um banco existente. Guarde uma cópia segura desse arquivo fora do repositório. Os limites de memória da aplicação são definidos no compose de produção.

O nome do projeto Compose é fixo (`votacao-desafio`), para manter os mesmos volumes nos próximos deploys. O banco e a API ficam acessíveis apenas pela rede interna dos containers. Somente o Caddy publica portas. Logs têm rotação e não incluem corpos de requisição.

## Cloudflare e HTTPS

Para a primeira emissão de certificado, use DNS-only e confirme que o hostname resolve para o servidor. O Caddy usa ACME e precisa receber as verificações de domínio. Depois de conferir HTTPS no servidor de origem, pode-se ativar o proxy da Cloudflare mantendo **Full (strict)**. Não usar Flexible nem criar cache de respostas da API.

Não é necessário compartilhar um token Cloudflare com o servidor. A alteração DNS é realizada separadamente por um administrador autorizado. Esta configuração não usa um túnel no computador do desenvolvedor; a VM deve continuar disponível sem depender dele.

## Atualizar e verificar

Selecione o novo commit aprovado e execute novamente `sudo ./deploy/start.sh <domínio>`. A senha e os volumes existentes são mantidos. Quando o volume PostgreSQL já existe, o script inicia e aguarda apenas o banco, inclusive se ele estiver parado, e cria um `pg_dump` compactado em `/opt/votacao-state/backups/` antes de atualizar a aplicação. O container existente do banco não é recriado nessa etapa. Se a preparação do banco ou o backup falhar, a atualização da aplicação é interrompida.

```bash
sudo docker compose --env-file /opt/votacao-state/.env -f deploy/compose.prod.yaml ps
curl --fail https://votacao.seu-dominio.com/actuator/health
python3 scripts/smoke.py create --base-url https://votacao.seu-dominio.com --state-file /tmp/votacao-smoke.json
# Reiniciar a aplicação, preservando volumes, e consultar os mesmos dados:
sudo docker compose --env-file /opt/votacao-state/.env -f deploy/compose.prod.yaml restart app db
python3 scripts/smoke.py verify --base-url https://votacao.seu-dominio.com --state-file /tmp/votacao-smoke.json
```

O smoke cria dados sintéticos identificados como teste. A comprovação de implantação pública fica em `docs/validacao.md`; adicionar arquivos de deploy ao Git não comprova que a hospedagem ocorreu.

## Persistência e encerramento

Reiniciar ou recriar containers preserva os volumes. **Excluir a VM, seu disco ou executar `down --volumes` pode apagar os dados.** O backup local protege atualizações, mas não a perda do próprio servidor; exporte o dump antes de desativar a hospedagem. Uma VM única é adequada à demonstração, sem alta disponibilidade.

Recursos de nuvem podem continuar sendo cobrados enquanto existirem, mesmo se a aplicação estiver parada. Ao fim da avaliação, remover a VM e os recursos associados com autorização do proprietário, depois de exportar os dados necessários.
