#!/usr/bin/env bash
# Provera verzija Flyway migracija (CI korak "Verzije migracija su jedinstvene"). Paralelne grane (MCP, slajd Zadatak,
# prijava) uzimaju broj migracije tek pri merge-u u staging, pa git može da spoji dva fajla iste verzije bez konflikta.
# Izlaz 1 kad:
#   - dve migracije u radnom stablu imaju istu verziju (Flyway bi odbio start);
#   - verzija koje nema u <ref> (osnova PR-a) nije veća od najveće verzije u <ref> (staging je već primenio veće, pa bi
#     Flyway novu ispod njih odbio kao "resolved locally but not applied");
#   - ime V-fajla nije V<broj>__<opis>.sql (Flyway ga ne bi prepoznao ili mu verzija ne bi bila ceo broj).
# Upotreba: scripts/provera-migracija.sh <ref>   (npr. origin/staging; CI: osnova PR-a, za push origin/staging)
# Izlaz 0 = u redu, 1 = prekršaj, 2 = pogrešna upotreba ili nepoznat ref. Ispisuje samo imena fajlova, nikad sadržaj.
set -euo pipefail

if (($# != 1)); then
  echo "Upotreba: $0 <ref>" >&2
  exit 2
fi
ref="$1"
MIGRATIONS="src/main/resources/db/migration"

cd "$(dirname "$0")/.."

if ! git rev-parse --verify --quiet "${ref}^{commit}" >/dev/null; then
  echo "Nepoznat ref: $ref (CI: actions/checkout sa fetch-depth: 0)" >&2
  exit 2
fi

ime_ok='^V([0-9]+)__[^[:space:]]+\.sql$'
greske=0

# radno stablo: ime -> verzija
declare -A verzija_fajla=()
shopt -s nullglob
for f in "$MIGRATIONS"/V*; do
  ime="$(basename "$f")"
  if [[ "$ime" =~ $ime_ok ]]; then
    verzija_fajla["$ime"]=$((10#${BASH_REMATCH[1]}))
  else
    echo "Ime migracije nije V<broj>__<opis>.sql: $ime"
    greske=1
  fi
done

# iste verzije u radnom stablu
declare -A po_verziji=()
for ime in "${!verzija_fajla[@]}"; do
  v="${verzija_fajla[$ime]}"
  po_verziji["$v"]+="${po_verziji[$v]:+ }$ime"
done
for v in $(printf '%s\n' "${!po_verziji[@]}" | sort -n); do
  read -r -a imena <<<"${po_verziji[$v]}"
  if ((${#imena[@]} > 1)); then
    echo "Ista verzija $v: $(printf '%s\n' "${imena[@]}" | sort | paste -sd ' ')"
    greske=1
  fi
done

# verzije u <ref>
declare -A u_refu=()
najveca=0
while IFS= read -r putanja; do
  ime="$(basename "$putanja")"
  [[ "$ime" =~ $ime_ok ]] || continue
  v=$((10#${BASH_REMATCH[1]}))
  u_refu["$v"]=1
  if ((v > najveca)); then najveca=$v; fi
done < <(git ls-tree -r --name-only "$ref" -- "$MIGRATIONS")

# nove verzije (nema ih u <ref>) moraju biti iznad najveće u <ref>
for ime in $(printf '%s\n' "${!verzija_fajla[@]}" | sort -V); do
  v="${verzija_fajla[$ime]}"
  if [[ -z "${u_refu[$v]:-}" ]] && ((v <= najveca)); then
    echo "Nova migracija $ime nije iznad najveće verzije u $ref (V$najveca)"
    greske=1
  fi
done

if ((greske)); then
  exit 1
fi
echo "Verzije migracija su jedinstvene i nove su iznad V$najveca iz $ref."
