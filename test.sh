#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/scripts/common.sh"

if [ "${1:-}" != '' ] && [ "${1:-}" != --e2e ]; then
  printf '%s\n' 'Uso: ./test.sh [--e2e]' >&2
  exit 1
fi
install_frontend
run_maven verify
(cd "$TASK_ROOT/frontend" && npm run format:check && npm test && npm run build)

if [ "${1:-}" = --e2e ]; then
  (cd "$TASK_ROOT/frontend" && npx playwright install chromium && npm run test:e2e)
fi
printf '\n%s\n' 'Verificações concluídas.'
