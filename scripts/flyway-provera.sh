#!/usr/bin/env bash
# Lokalna provera Flyway migracija (V1 baseline, V2-V4 idempotentne, i svaka sledeća) nad pet početnih stanja šeme:
#   prazna         - prazna baza (kao CI gftest): očekivano sve od V1 izvršeno
#   gf             - šema produkcione baze pre onboardinga: očekivano baseline 1 + sve migracije posle V1
#   gf_staging     - šema staging baze (onboarding već postoji): očekivano baseline 1 + V2 (no-op) + ostale
#   vec_migrirana  - gf_staging šema kojoj su objekti svih migracija od V3 nadalje (V3, V4, V6, V7 uživo, ...) već ručno
#                    primenjeni, a Flyway istorije nema (kao reset staging baze iz dump-a posle migracije):
#                    baseline 1 + V2 i sve sledeće moraju da prođu kao no-op
#   rollback       - šema gf, novi jar je migrira do kraja i staje; JDBC upiše predmet, prezentaciju i INFO slajd, pa stara
#                    slika (OLD_IMAGE, trenutna prod slika) mora da startuje nad novom šemom, prođe jedan PUT slajda i
#                    pokrene pa završi izvođenje (upis u izvodjenja bez novih kolona; rollback bez DB posla: DEFAULT u
#                    DDL-u, nullable kolone, Flyway preskače buduće verzije). Preskače se sa porukom kad slike OLD_IMAGE
#                    nema.
# Za svaki scenario pravi bazu ${DB_PREFIX}<scenario> u test MySQL-u, učita dump šeme (bez podataka), pokrene jar
# (ddl-auto=validate) i ispiše flyway_schema_history. Izlaz != 0 ako bilo koji scenario ne prođe.
#
# Pre pokretanja: ./mvnw -B -DskipTests package
# Promenljive: MYSQL_CONTAINER (gfs-redizajn-mysql), MYSQL_PORT (3307), APP_PORT (18082), DB_PREFIX (fw_; svaki worktree
#              svoj, npr. fw_mcp_, da paralelne provere ne brišu tuđe baze), OLD_IMAGE (gfs-backend:latest),
#              SCHEMA_DIR (/data/tmp/redizajn-schema, fajlovi gf.sql i gf_staging.sql), START_TIMEOUT (120 s; start
#              traje 20-45 s kad paralelno rade i drugi build-ovi).
# Nikad ne pokretati nad shared-mysql (3306): skripta briše i ponovo pravi baze ${DB_PREFIX}*.
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-gfs-redizajn-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
APP_PORT="${APP_PORT:-18082}"
DB_PREFIX="${DB_PREFIX:-fw_}"
OLD_IMAGE="${OLD_IMAGE:-gfs-backend:latest}"
SCHEMA_DIR="${SCHEMA_DIR:-/data/tmp/redizajn-schema}"
START_TIMEOUT="${START_TIMEOUT:-120}"
MIGRATIONS="src/main/resources/db/migration"
SCENARIJI=(prazna gf gf_staging vec_migrirana rollback)

cd "$(dirname "$0")/.."

if [[ "$MYSQL_PORT" == "3306" ]]; then
  echo "MYSQL_PORT=3306 je shared-mysql, odbijam." >&2
  exit 2
fi
# prefiks ide u ime baze (SQL) i u ime kontejnera stare slike
if [[ ! "$DB_PREFIX" =~ ^[a-z][a-z0-9_]{0,30}$ ]]; then
  echo "DB_PREFIX mora odgovarati ^[a-z][a-z0-9_]{0,30}\$." >&2
  exit 2
fi
if [[ ! "$APP_PORT" =~ ^[0-9]{4,5}$ ]]; then
  echo "APP_PORT mora biti broj porta." >&2
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
stara_app="${DB_PREFIX}rollback-app"
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
zaustavi_staru() { docker rm -f "$stara_app" >/dev/null 2>&1 || true; }
pocisti() { zaustavi_app; zaustavi_staru; }
trap pocisti EXIT

# Stara slika nad bazom $1 (posle novog jar-a): kontejner u mrežnom prostoru test MySQL-a (127.0.0.1:3306 je on), Started,
# pa kroz curl iz kontejnera PUT INFO slajda $3, pokretanje izvođenja prezentacije $2 ("ne čuvaj") i komanda ZAVRSI. Vraća 0
# kad PUT vrati izmenjen naslov, pokretanje vrati izvođenje sa kodom i ZAVRSI odgovori 200.
proba_stare_slike() {
  local db="$1" prezentacija="$2" slajd="$3" stari_log="$4" i stanje odgovor izv http
  zaustavi_staru
  docker run -d --rm --name "$stara_app" --network "container:$MYSQL_CONTAINER" \
    -e SPRING_PROFILES_ACTIVE=dev \
    -e SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/${db}" \
    -e SPRING_DATASOURCE_USERNAME=root \
    -e SPRING_DATASOURCE_PASSWORD= \
    -e SERVER_PORT="$APP_PORT" \
    -e GFS_MEDIJI_DIR=/tmp/gfs-mediji \
    "$OLD_IMAGE" >/dev/null
  stanje="timeout"
  for ((i = 0; i < START_TIMEOUT; i++)); do
    # --rm briše kontejner (i log) kad izađe, pa se log prepisuje u fajl svake sekunde
    docker logs "$stara_app" >"$stari_log" 2>&1 || { stanje="izašao"; break; }
    if grep -q "Started GfsSystemApplication" "$stari_log"; then stanje="ok"; break; fi
    sleep 1
  done
  if [[ "$stanje" != "ok" ]]; then
    echo "STARA SLIKA: NEUSPEH ($stanje), log: $stari_log"
    grep -E "ERROR|Caused by|Exception" "$stari_log" | head -n 15 || true
    zaustavi_staru
    return 1
  fi
  echo "STARA SLIKA: OK ($(grep -o 'Started GfsSystemApplication in [0-9.]* seconds' "$stari_log"))"
  grep -E "Flyway|Successfully validated|Schema .* is up to date|newer than" "$stari_log" \
    | sed -E 's/^.*(INFO|WARN|ERROR) +[0-9]+ --- (\[[^]]*\] *)+[^ ]+ +: /  /' || true
  odgovor="$(docker exec "$stara_app" curl -fsS -X PUT -H 'Content-Type: application/json' \
    --data '{"tip":"INFO","naslov":"Rollback proba","postepeno":false}' \
    "http://127.0.0.1:${APP_PORT}/slajdovi/${slajd}" 2>&1)" || odgovor="PUT nije uspeo: $odgovor"
  if [[ "$odgovor" != *'"naslov":"Rollback proba"'* ]]; then
    docker logs "$stara_app" >"$stari_log" 2>&1 || true
    zaustavi_staru
    echo "PUT /slajdovi/$slajd (stara slika): NEUSPEH, log: $stari_log"
    printf '  %s\n' "${odgovor:0:300}"
    return 1
  fi
  echo "PUT /slajdovi/$slajd (stara slika): OK"
  # izvođenje: INSERT u izvodjenja bez novih kolona (DEFAULT u DDL-u), pa ZAVRSI; odgovor počinje sa {"id":<izvođenje>
  odgovor="$(docker exec "$stara_app" curl -fsS -X POST -H 'Content-Type: application/json' --data '{"cuvanje":false}' \
    "http://127.0.0.1:${APP_PORT}/prezentacije/${prezentacija}/izvodjenja" 2>&1)" || odgovor="POST nije uspeo: $odgovor"
  izv="$(sed -nE 's/^\{"id":([0-9]+),.*/\1/p' <<<"$odgovor")"
  if [[ -z "$izv" || "$odgovor" != *'"kod":"'* ]]; then
    docker logs "$stara_app" >"$stari_log" 2>&1 || true
    zaustavi_staru
    echo "POST /prezentacije/$prezentacija/izvodjenja (stara slika): NEUSPEH, log: $stari_log"
    printf '  %s\n' "${odgovor:0:300}"
    return 1
  fi
  echo "POST /prezentacije/$prezentacija/izvodjenja (stara slika): OK (izvođenje $izv)"
  http="$(docker exec "$stara_app" curl -sS -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    --data '{"tip":"ZAVRSI"}' "http://127.0.0.1:${APP_PORT}/izvodjenja/${izv}/komande" 2>&1)" || http="curl: $http"
  docker logs "$stara_app" >"$stari_log" 2>&1 || true
  zaustavi_staru
  if [[ "$http" == "200" ]]; then
    echo "POST /izvodjenja/$izv/komande ZAVRSI (stara slika): OK"
    return 0
  fi
  echo "POST /izvodjenja/$izv/komande ZAVRSI (stara slika): NEUSPEH ($http), log: $stari_log"
  return 1
}

neuspeli=()
preskoceni=()
prosli=()
for s in "${SCENARIJI[@]}"; do
  db="${DB_PREFIX}${s}"
  echo "==================== scenario: $s (baza $db)"
  if [[ "$s" == "rollback" ]] && ! docker image inspect "$OLD_IMAGE" >/dev/null 2>&1; then
    echo "PRESKOČENO: nema slike $OLD_IMAGE (OLD_IMAGE)"
    preskoceni+=("$s")
    continue
  fi
  mysql_exec -e "DROP DATABASE IF EXISTS \`$db\`; CREATE DATABASE \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  if [[ "$s" != "prazna" ]]; then
    dump="$SCHEMA_DIR/$s.sql"
    [[ "$s" == "vec_migrirana" ]] && dump="$SCHEMA_DIR/gf_staging.sql"
    [[ "$s" == "rollback" ]] && dump="$SCHEMA_DIR/gf.sql"
    [[ -f "$dump" ]] || { echo "Nema dump-a $dump" >&2; exit 2; }
    mysql_exec "$db" < "$dump"
  fi
  if [[ "$s" == "vec_migrirana" ]]; then
    # objekti svih migracija od V3 nadalje već postoje, ali bez flyway_schema_history (po broju verzije, ne po imenu)
    for f in $(printf '%s\n' "$MIGRATIONS"/V*__*.sql | sort -V); do
      v="$(basename "$f" | sed -E 's/^V([0-9]+)__.*/\1/')"
      if ((v >= 3)); then
        echo "  ručno primenjeno: $(basename "$f")"
        mysql_exec "$db" < "$f"
      fi
    done
  fi

  log="$(mktemp "/tmp/flyway-provera-${DB_PREFIX}$s.XXXX.log")"
  SPRING_PROFILES_ACTIVE=dev \
  SPRING_DATASOURCE_URL="jdbc:mysql://localhost:${MYSQL_PORT}/${db}" \
  SPRING_DATASOURCE_USERNAME=root \
  SPRING_DATASOURCE_PASSWORD='' \
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

  if [[ "$s" == "rollback" && "$rezultat" == "ok" ]]; then
    # podaci koje stara slika menja: predmet, prezentacija i INFO slajd (bez novih kolona, kao što ih stara slika piše)
    read -r prezentacija slajd < <(mysql_exec -N -B "$db" -e "
      INSERT INTO predmeti (naziv) VALUES ('Rollback proba');
      INSERT INTO prezentacije (predmet_id, naziv, takmicenje, telefon_prikaz, detalji_dozvoljeni, kreirano, izmenjeno)
        VALUES (LAST_INSERT_ID(), 'Rollback proba', 0, 'DUGMAD', 0, NOW(6), NOW(6));
      SET @prezentacija := LAST_INSERT_ID();
      INSERT INTO slajdovi (prezentacija_id, rb, tip, naslov, sadrzaj, postepeno)
        VALUES (@prezentacija, 1, 'INFO', 'Pre rollback-a', 'Tekst slajda', 0);
      SELECT @prezentacija, LAST_INSERT_ID();")
    if ! proba_stare_slike "$db" "$prezentacija" "$slajd" "${log%.log}-stara.log"; then
      neuspeli+=("$s")
      rezultat="stara slika"
    fi
  fi
  [[ "$rezultat" == "ok" ]] && prosli+=("$s")

  echo "flyway_schema_history:"
  mysql_exec -t "$db" -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank" \
    2>/dev/null || echo "  (nema tabele flyway_schema_history)"
done

echo "===================="
if ((${#preskoceni[@]})); then
  echo "Preskočeni scenariji: ${preskoceni[*]}"
fi
if ((${#neuspeli[@]})); then
  echo "NEUSPEŠNI scenariji: ${neuspeli[*]}"
  exit 1
fi
echo "Svi scenariji prolaze: ${prosli[*]}"
