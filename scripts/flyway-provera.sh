#!/usr/bin/env bash
# Lokalna provera Flyway migracija (V1 baseline, V2-V4 idempotentne, i svaka sledeća) nad četiri početna stanja šeme:
#   prazna         - prazna baza (kao CI gftest): očekivano sve od V1 izvršeno
#   gf             - šema produkcione baze pre onboardinga: očekivano baseline 1 + sve migracije posle V1
#   gf_staging     - šema staging baze (onboarding već postoji): očekivano baseline 1 + V2 (no-op) + ostale
#   vec_migrirana  - gf_staging šema kojoj su V3/V4/V6 objekti već ručno primenjeni, a Flyway istorije nema (kao reset
#                    staging baze iz dump-a posle migracije): baseline 1 + V2..V6 moraju da prođu kao no-op
# Za svaki scenario pravi bazu fw_<scenario> u test MySQL-u, učita dump šeme (bez podataka), pokrene jar
# (ddl-auto=validate) i ispiše flyway_schema_history. Izlaz != 0 ako bilo koji scenario ne startuje.
#
# Pre pokretanja: ./mvnw -B -DskipTests package
# Promenljive: MYSQL_CONTAINER (gfs-redizajn-mysql), MYSQL_PORT (3307), APP_PORT (18082),
#              SCHEMA_DIR (/data/tmp/redizajn-schema, fajlovi gf.sql i gf_staging.sql), START_TIMEOUT (60 s).
# Nikad ne pokretati nad shared-mysql (3306): skripta briše i ponovo pravi baze fw_*.
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-gfs-redizajn-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
APP_PORT="${APP_PORT:-18082}"
SCHEMA_DIR="${SCHEMA_DIR:-/data/tmp/redizajn-schema}"
START_TIMEOUT="${START_TIMEOUT:-60}"
MIGRATIONS="src/main/resources/db/migration"
SCENARIJI=(prazna gf gf_staging vec_migrirana)

cd "$(dirname "$0")/.."

if [[ "$MYSQL_PORT" == "3306" ]]; then
  echo "MYSQL_PORT=3306 je shared-mysql, odbijam." >&2
  exit 2
fi

shopt -s nullglob
jar=""
for j in target/*.jar; do [[ "$j" == *-plain.jar ]] || { jar="$j"; break; }; done
if [[ -z "$jar" ]]; then
  echo "Nema jar-a u target/. Prvo: ./mvnw -B -DskipTests package" >&2
  exit 2
fi

mysql_exec() { docker exec -i "$MYSQL_CONTAINER" mysql -uroot "$@"; }

app_pid=""
log=""
zaustavi_app() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill "$app_pid" 2>/dev/null || true
    local i
    for ((i = 0; i < 20; i++)); do   # do 10 s za uredno gašenje, pa SIGKILL
      kill -0 "$app_pid" 2>/dev/null || break
      sleep 0.5
    done
    kill -9 "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
  fi
  app_pid=""
}
trap zaustavi_app EXIT

neuspeli=()
for s in "${SCENARIJI[@]}"; do
  db="fw_${s}"
  echo "==================== scenario: $s (baza $db)"
  mysql_exec -e "DROP DATABASE IF EXISTS \`$db\`; CREATE DATABASE \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  if [[ "$s" != "prazna" ]]; then
    dump="$SCHEMA_DIR/$s.sql"
    [[ "$s" == "vec_migrirana" ]] && dump="$SCHEMA_DIR/gf_staging.sql"
    [[ -f "$dump" ]] || { echo "Nema dump-a $dump" >&2; exit 2; }
    mysql_exec "$db" < "$dump"
  fi
  if [[ "$s" == "vec_migrirana" ]]; then
    # objekti iz V3, V4 i V6 već postoje, ali bez flyway_schema_history
    for m in V3 V4 V6; do
      mysql_exec "$db" < "$MIGRATIONS"/${m}__*.sql
    done
  fi

  log="$(mktemp "/tmp/flyway-provera-$s.XXXX.log")"
  SPRING_PROFILES_ACTIVE=dev \
  SPRING_DATASOURCE_URL="jdbc:mysql://localhost:${MYSQL_PORT}/${db}" \
  SPRING_DATASOURCE_USERNAME=root \
  SPRING_DATASOURCE_PASSWORD= \
  SERVER_PORT="$APP_PORT" \
    java -jar "$jar" >"$log" 2>&1 &
  app_pid=$!

  rezultat="timeout"
  for ((i = 0; i < START_TIMEOUT; i++)); do
    if grep -q "Started GfsSystemApplication" "$log"; then rezultat="ok"; break; fi
    if ! kill -0 "$app_pid" 2>/dev/null; then rezultat="izašao"; break; fi
    sleep 1
  done
  zaustavi_app

  if [[ "$rezultat" == "ok" ]]; then
    echo "START: OK ($(grep -o 'Started GfsSystemApplication in [0-9.]* seconds' "$log"))"
  else
    echo "START: NEUSPEH ($rezultat), log: $log"
    grep -E "ERROR|Caused by|Exception" "$log" | head -n 15 || true
    neuspeli+=("$s")
  fi
  grep -E "Flyway|Migrating schema|Successfully (applied|baselined|validated)|Creating Schema History" "$log" \
    | sed -E 's/^.*(INFO|WARN|ERROR) +[0-9]+ --- (\[[^]]*\] *)+[^ ]+ +: /  /' || true
  echo "flyway_schema_history:"
  mysql_exec -t "$db" -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank" \
    2>/dev/null || echo "  (nema tabele flyway_schema_history)"
done

echo "===================="
if ((${#neuspeli[@]})); then
  echo "NEUSPEŠNI scenariji: ${neuspeli[*]}"
  exit 1
fi
echo "Svi scenariji startuju: ${SCENARIJI[*]}"
