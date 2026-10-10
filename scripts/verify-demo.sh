#!/bin/sh
set -eu
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
# Curl runs inside the app image; only Git + Docker Compose are needed on the host.
docker compose exec -T app sh -s <<'CHECKS'
set -eu
base=http://localhost:8080
curl -fsS "$base/actuator/health" | grep -q '"status":"UP"'
curl -fsS "$base/api/v1/site" | grep -q '"navigation"'
curl -fsS "$base/api/v1/posts/start-with-a-note" | grep -q '"bodyHtml"'
curl -fsS "$base/api/v1/posts/code-and-tables" | grep -q '<table>'
curl -fsS "$base/api/v1/posts/a-long-title" | grep -q '"tags":\[\]'
for slug in unpublished-example excluded-draft private-example not-a-real-post; do
  status=$(curl -sS -o /dev/null -w '%{http_code}' "$base/api/v1/posts/$slug")
  [ "$status" = 404 ] || { echo "Expected 404 for $slug, got $status" >&2; exit 1; }
done
status=$(curl -sS -o /dev/null -w '%{http_code}' "$base/api/v1/posts?page=-1")
[ "$status" = 400 ]
for path in / /archive /notes /lab /about /p/images-and-space; do
  curl -fsS -o /dev/null "$base$path"
done
echo 'Demo health, pages, API, formatting, pagination and publication exclusions passed.'
CHECKS
