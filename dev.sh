#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/scripts/common.sh"

prepare_java
install_frontend
for task_port in 8080 5173; do
  if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"$task_port" -sTCP:LISTEN >/dev/null 2>&1; then
    printf 'A porta %s está ocupada. Encerre a aplicação correspondente antes de iniciar.\n' "$task_port" >&2
    exit 1
  fi
done

printf '%s\n' 'Compilando o backend...'
run_maven -DskipTests package
mkdir -p "$TASK_ROOT/.run"

backend_pid=''
frontend_pid=''
cleanup() {
  trap - EXIT INT TERM
  [ -z "$frontend_pid" ] || kill "$frontend_pid" 2>/dev/null || true
  [ -z "$backend_pid" ] || kill "$backend_pid" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

(cd "$TASK_ROOT/backend" && exec java -jar target/votacao.jar --server.address=127.0.0.1) >"$TASK_ROOT/.run/backend.log" 2>&1 &
backend_pid=$!

ready=false
for attempt in {1..60}; do
  if curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null; then
    ready=true
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    cat "$TASK_ROOT/.run/backend.log" >&2
    exit 1
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  printf '%s\n' 'Backend não respondeu em 60 segundos. Consulte .run/backend.log.' >&2
  exit 1
fi

(cd "$TASK_ROOT/frontend" && exec node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort) &
frontend_pid=$!
printf '\n%s\n' 'Aplicação: http://localhost:5173' 'API/Swagger: http://localhost:8080/swagger-ui/index.html' 'Logs do backend: .run/backend.log' 'Ctrl+C encerra os dois processos. Os dados ficam em backend/data/.'

while kill -0 "$backend_pid" 2>/dev/null && kill -0 "$frontend_pid" 2>/dev/null; do
  sleep 2
done
printf '%s\n' 'Um dos processos encerrou. Verifique a saída acima e .run/backend.log.' >&2
exit 1
