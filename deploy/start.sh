#!/usr/bin/env bash
# Instala/atualiza a aplicação no servidor. Não cria recursos ou altera DNS.
set -euo pipefail
umask 077

if [ "$(id -u)" -ne 0 ]; then
  printf '%s\n' 'Execute com sudo no servidor da aplicação.' >&2
  exit 1
fi
TASK_DOMAIN="${1:-}"
if [[ ! "$TASK_DOMAIN" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?\.[a-z]{2,}$ ]]; then
  printf '%s\n' 'Uso: sudo ./deploy/start.sh votacao.seu-dominio.com' >&2
  exit 1
fi
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_STATE_DIR=/opt/votacao-state
install -d -m 0700 "$TASK_STATE_DIR" "$TASK_STATE_DIR/backups"
if [ ! -f "$TASK_STATE_DIR/.env" ]; then
  printf 'DOMAIN=%s\nPOSTGRES_PASSWORD=%s\n' "$TASK_DOMAIN" "$(openssl rand -hex 32)" > "$TASK_STATE_DIR/.env"
fi
chmod 0600 "$TASK_STATE_DIR/.env"
if ! grep -Fxq "DOMAIN=$TASK_DOMAIN" "$TASK_STATE_DIR/.env"; then
  printf '%s\n' 'O domínio difere da configuração existente. Revise /opt/votacao-state/.env antes de continuar.' >&2
  exit 1
fi

compose() {
  docker compose --env-file "$TASK_STATE_DIR/.env" -f "$TASK_ROOT/deploy/compose.prod.yaml" "$@"
}
compose config --quiet
compose build app

# Backup antes de atualizar uma instalação existente. A senha nunca é impressa.
if [ -n "$(compose ps --status running -q db)" ]; then
  compose exec -T db pg_dump -U votacao -d votacao | gzip > "$TASK_STATE_DIR/backups/antes-do-deploy-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
fi
compose up --detach --wait --wait-timeout 180
curl --fail --silent --show-error --retry 15 --retry-delay 4 --retry-all-errors --max-time 15 "https://$TASK_DOMAIN/actuator/health"
printf '\nAplicação disponível em https://%s\n' "$TASK_DOMAIN"
