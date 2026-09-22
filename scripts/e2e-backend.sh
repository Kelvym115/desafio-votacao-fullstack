#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
prepare_java
if [ ! -f "$TASK_ROOT/backend/target/votacao.jar" ]; then
  printf '%s\n' 'Compile primeiro: ./test.sh ou ./dev.sh.' >&2
  exit 1
fi
mkdir -p "$TASK_ROOT/.run"
task_database="$(mktemp -d "$TASK_ROOT/.run/e2e-XXXXXX")"
cd "$TASK_ROOT/backend"
exec java -jar target/votacao.jar --server.port=18080 --server.address=127.0.0.1 \
  "--spring.datasource.url=jdbc:h2:file:$task_database/votacao;DB_CLOSE_ON_EXIT=FALSE" \
  --logging.level.br.com.db.votacao=WARN
