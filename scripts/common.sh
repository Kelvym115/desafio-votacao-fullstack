#!/usr/bin/env bash
# Funções compartilhadas; não altera instalações globais da máquina.
set -euo pipefail

TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export MAVEN_USER_HOME="$TASK_ROOT/.cache/maven"
export npm_config_cache="$TASK_ROOT/.cache/npm"
export PLAYWRIGHT_BROWSERS_PATH="$TASK_ROOT/.cache/ms-playwright"

node_supported() {
  command -v node >/dev/null 2>&1 && node -e 'const [major,minor]=process.versions.node.split(".").map(Number);process.exit(major>=24||(major===22&&minor>=12)?0:1)' >/dev/null 2>&1
}

prepare_node() {
  if node_supported; then return; fi
  # Aproveita uma versão compatível já instalada pelo nvm, sem trocar a padrão.
  local candidate
  for candidate in "$HOME"/.nvm/versions/node/v24*/bin "$HOME"/.nvm/versions/node/v22*/bin; do
    if [ -x "$candidate/node" ]; then
      export PATH="$candidate:$PATH"
      if node_supported; then return; fi
    fi
  done
  printf '%s\n' 'É necessário Node.js 24 LTS (ou 22.12+). Com nvm: nvm install && nvm use' >&2
  exit 1
}

prepare_java() {
  # No macOS ARM, prefere o JDK ARM já instalado quando JAVA_HOME não foi definido.
  if [ -z "${JAVA_HOME:-}" ] && [ "$(uname -m)" = arm64 ] && [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17
  fi
  if [ -n "${JAVA_HOME:-}" ]; then
    if [ ! -x "$JAVA_HOME/bin/java" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
      printf '%s\n' 'JAVA_HOME precisa apontar para um JDK válido, com java e javac.' >&2
      exit 1
    fi
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
  if ! command -v java >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1; then
    printf '%s\n' 'Instale um JDK 17 ou superior e configure JAVA_HOME.' >&2
    exit 1
  fi
  local task_java_version
  if ! task_java_version="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/^[[:space:]]*java.specification.version =/ {gsub(/[[:space:]]/, "", $2); print $2}')"; then
    printf '%s\n' 'Não foi possível executar o JDK configurado.' >&2
    exit 1
  fi
  if [[ ! "$task_java_version" =~ ^[0-9]+$ ]] || [ "$task_java_version" -lt 17 ]; then
    printf '%s\n' 'É necessário JDK 17 ou superior. Confira JAVA_HOME e PATH.' >&2
    exit 1
  fi
}

install_frontend() {
  prepare_node
  if [ ! -f "$TASK_ROOT/frontend/node_modules/.package-lock.json" ] ||
     [ "$TASK_ROOT/frontend/package-lock.json" -nt "$TASK_ROOT/frontend/node_modules/.package-lock.json" ]; then
    (cd "$TASK_ROOT/frontend" && npm ci --no-fund --no-audit)
  fi
}

run_maven() {
  prepare_java
  (cd "$TASK_ROOT/backend" && ./mvnw -B -ntp "-Dmaven.repo.local=$TASK_ROOT/.cache/m2" "$@")
}
