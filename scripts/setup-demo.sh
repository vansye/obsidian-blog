#!/bin/sh
set -eu

case "${1:-}" in
  '') start=true ;;
  --prepare-only) start=false ;;
  *) echo 'Usage: sh scripts/setup-demo.sh [--prepare-only]' >&2; exit 2 ;;
esac
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
command -v git >/dev/null 2>&1 || { echo 'Git is required.' >&2; exit 1; }
if [ "$start" = true ]; then
  docker compose version >/dev/null
fi
if [ -e data/vault ] || [ -L data/vault ]; then
  echo 'Refusing to overwrite data/vault. Reuse it with docker compose up --build -d --wait.' >&2
  exit 1
fi
umask 077
if [ ! -e .env ]; then
  random_secret() { od -An -N24 -tx1 /dev/urandom | tr -d ' \n'; }
  db_password=$(random_secret)
  root_password=$(random_secret)
  redis_password=$(random_secret)
  webhook_secret=$(random_secret)
  (set -C; cat > .env <<EOF
DB_PASSWORD=$db_password
MYSQL_ROOT_PASSWORD=$root_password
REDIS_PASSWORD=$redis_password
VAULT_WEBHOOK_SECRET=$webhook_secret
HOST_BIND=127.0.0.1
PORT=8080
SITE_NAME=纸间
SITE_DESCRIPTION=用 Obsidian 写作，让想法自然生长。
SITE_AUTHOR=写作者
SITE_URL=http://localhost:8080
SITE_GITHUB=
SITE_EMAIL=
PUBLISH_ROOT=博客
CORS_ORIGINS=
SYNC_FETCH_ENABLED=false
SYNC_STARTUP_ENABLED=true
SYNC_POLL_ENABLED=true
SYNC_POLL_INTERVAL=PT5S
SYNC_POLL_INITIAL_DELAY=PT5S
EOF
  )
else
  echo 'Keeping existing .env unchanged.'
fi
# The non-root app user must read the demo checkout through its read-only mount.
umask 022
mkdir -p data
mkdir data/vault
cp -R example-vault/. data/vault/
git -C data/vault init --quiet
git -C data/vault config user.name 'Blog Demo'
git -C data/vault config user.email 'demo@example.invalid'
git -C data/vault add .
git -C data/vault -c commit.gpgsign=false commit --quiet -m 'Initialize public demo vault'
echo 'Prepared data/vault and .env. Only committed content is published.'
if [ "$start" = true ]; then
  docker compose up --build -d --wait
  echo 'Open http://localhost:8080 (or the PORT configured in .env).'
fi
