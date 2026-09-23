#!/usr/bin/env bash
# Preparação de uma VM Ubuntu dedicada. Executar como root, sem credenciais na CLI.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  printf '%s\n' 'Execute com sudo em um servidor Ubuntu dedicado.' >&2
  exit 1
fi
source /etc/os-release
if [ "$ID" != ubuntu ]; then
  printf '%s\n' 'Este instalador foi preparado para Ubuntu.' >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git openssl
if ! docker compose version >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  cat > /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
docker compose version
