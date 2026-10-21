#!/usr/bin/env bash
# Smoke test render servisa gfs-latex (spec banke 3.1): izgradi image, pokrene kontejner sa izolacijom iz compose-a
# (read-only, tmpfs /tmp, bez capability-ja, no-new-privileges, ograničenja memorije/CPU/procesa) i proveri API:
# SVG i PDF primera sa podrazumevanom preambulom (TikZ automat + priloženi PNG), telo poslato kao chunked (tako šalje
# Java klijent), SVG i PNG iz jednog prevođenja (formati, spec MCP 6.4), grešku sa linijom, zabranjene komande
# (\write18, \input van radnog dir-a), dokument bez strana koji sam napiše dokument.pdf, 400/404/405/413 i zdravlje.
# Pokretanje iz bilo kog direktorijuma: bash latex-render/test/smoke.sh (treba docker, curl, python3). Izlaz != 0 na grešku.
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVIS_DIR="$(dirname "$TEST_DIR")"
REPO_DIR="$(dirname "$SERVIS_DIR")"
JAVA_DOKUMENT="$REPO_DIR/src/main/java/tri/novica/gfssystem/service/banka/LatexDokument.java"
IME=gfs-latex-ci
URL="http://127.0.0.1:18800"
TMP="$(mktemp -d)"
# 1x1 PNG, prilog "slika" za \includegraphics{slika} u primer.tex
PNG_1X1='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='

pocisti() { docker rm -f "$IME" >/dev/null 2>&1 || true; rm -rf "$TMP"; }
trap pocisti EXIT

pad() {
  echo "SMOKE FAIL: $*" >&2
  echo "--- docker logs $IME (kraj) ---" >&2
  docker logs --tail 40 "$IME" 2>&1 | sed 's/^/  /' >&2 || true
  exit 1
}
uspeh() { echo "ok   $*"; }

# Telo POST /render: python3 -I -c "$PY_ZAHTEV" <tex> <format> <izlaz.json> [ime:ekstenzija:putanja ...]
# <format> sa zarezom (npr. svg,png) je lista formati, a format je prvi (tako šalje Java klijent)
PY_ZAHTEV='
import base64, json, sys
tex_put, format_, izlaz = sys.argv[1:4]
formati = format_.split(",") if "," in format_ else None
prilozi = []
for arg in sys.argv[4:]:
    ime, ekstenzija, put = arg.split(":", 2)
    with open(put, "rb") as f:
        prilozi.append({"ime": ime, "ekstenzija": ekstenzija, "sadrzaj": base64.b64encode(f.read()).decode("ascii")})
with open(tex_put, encoding="utf-8") as f:
    tex = f.read()
telo = {"format": formati[0] if formati else format_, "tex": tex, "prilozi": prilozi, "timeoutSekunde": 20}
if formati:
    telo["formati"] = formati
with open(izlaz, "w", encoding="utf-8") as f:
    json.dump(telo, f)
'

# Provere odgovora: python3 -I -c "$PY_PROVERA" <mod> <odgovor.json> [argumenti]; ispiše razlog i izađe sa 1 kad ne prođe
PY_PROVERA='
import base64, json, re, sys
mod, put = sys.argv[1], sys.argv[2]
with open(put, encoding="utf-8") as f:
    o = json.load(f)

def ne(razlog):
    print(razlog)
    if isinstance(o, dict) and isinstance(o.get("log"), str):
        print("--- log (kraj) ---")
        print("\n".join(o["log"].splitlines()[-15:]))
    sys.exit(1)

def neuspeh_sa_greskom():
    if o.get("ok") is not False:
        ne("ok nije false")
    g = o.get("greska")
    if not isinstance(g, dict) or not isinstance(g.get("poruka"), str) or not g["poruka"]:
        ne("ok=false bez greska.poruka: %r" % (g,))
    log = o.get("log")
    if not isinstance(log, str) or not log.strip():
        ne("ok=false bez loga")
    if len(log.splitlines()) > 60:
        ne("log ima %d linija (najviše 60)" % len(log.splitlines()))
    return g, log

if mod == "uspeh":
    format_ = sys.argv[3]
    if o.get("ok") is not True:
        ne("ok nije true, greska=%s" % json.dumps(o.get("greska"), ensure_ascii=False))
    if not isinstance(o.get("trajanjeMs"), int):
        ne("nema trajanjeMs")
    sadrzaj = base64.b64decode(o.get(format_) or "", validate=True)
    if format_ == "svg":
        pocetak = sadrzaj.lstrip()[:5]
        if not (pocetak.startswith(b"<?xml") or pocetak.startswith(b"<svg")):
            ne("SVG ne počinje sa <?xml ili <svg: %r" % sadrzaj[:40])
        if len(sadrzaj) <= 500:
            ne("SVG je premali: %d bajtova" % len(sadrzaj))
        if b"<path" not in sadrzaj:
            ne("SVG nema <path (glifovi i TikZ kao putanje)")
        if b"<image" not in sadrzaj:
            ne("SVG nema <image (priloženi PNG)")
    elif not sadrzaj.startswith(b"%PDF-"):
        ne("PDF ne počinje sa %%PDF-: %r" % sadrzaj[:20])
    print("%s, %d bajtova, %d ms" % (format_, len(sadrzaj), o["trajanjeMs"]))
elif mod == "formati":
    formati = sys.argv[3].split(",")
    if o.get("ok") is not True:
        ne("ok nije true, greska=%s" % json.dumps(o.get("greska"), ensure_ascii=False))
    if not isinstance(o.get("trajanjeMs"), int):
        ne("nema trajanjeMs")
    visak = sorted(k for k in ("svg", "pdf", "png") if k in o and k not in formati)
    if visak:
        ne("odgovor nosi i netražene formate: %s" % ", ".join(visak))
    opis = []
    for format_ in formati:
        sadrzaj = base64.b64decode(o.get(format_) or "", validate=True)
        if format_ == "png":
            if not sadrzaj.startswith(b"\x89PNG\r\n\x1a\n"):
                ne("PNG ne počinje sa \\x89PNG: %r" % sadrzaj[:20])
            sirina, visina = int.from_bytes(sadrzaj[16:20], "big"), int.from_bytes(sadrzaj[20:24], "big")
            if max(sirina, visina) != 1400:
                ne("PNG je %dx%d, duža ivica treba da bude 1400 (-scale-to 1400)" % (sirina, visina))
            opis.append("png %dx%d, %d bajtova" % (sirina, visina, len(sadrzaj)))
        elif format_ == "svg":
            if b"<path" not in sadrzaj or b"<image" not in sadrzaj:
                ne("SVG bez <path ili <image")
            opis.append("svg %d bajtova" % len(sadrzaj))
        elif not sadrzaj.startswith(b"%PDF-"):
            ne("PDF ne počinje sa %%PDF-: %r" % sadrzaj[:20])
        else:
            opis.append("pdf %d bajtova" % len(sadrzaj))
    print("%s, %d ms" % ("; ".join(opis), o["trajanjeMs"]))
elif mod == "greska":
    ocekivana = int(sys.argv[3])
    g, log = neuspeh_sa_greskom()
    if type(g.get("linija")) is not int:
        ne("greska.linija nije ceo broj: %r" % (g.get("linija"),))
    if g["linija"] != ocekivana:
        ne("greska.linija je %d, očekivano %d (linija u celom dokumentu)" % (g["linija"], ocekivana))
    if "Undefined control sequence" not in g["poruka"]:
        ne("poruka bez Undefined control sequence: %r" % g["poruka"])
    print("linija %d: %s" % (g["linija"], g["poruka"]))
elif mod == "zabranjeno":
    g, log = neuspeh_sa_greskom()
    svg = base64.b64decode(o.get("svg") or "").decode("utf-8", "replace")
    for gde, tekst in (("log", log), ("greska", g["poruka"]), ("svg", svg)):
        for trag in ("uid=", "root:x:"):
            if trag in tekst:
                ne("%r u polju %s: shell komanda ili /etc/passwd su prošli" % (trag, gde))
        # TeX ne prepisuje pročitan fajl u log, ali ga otvori "(/etc/passwd" i pokaže red na kom je stao
        if "(/etc/passwd" in tekst or re.search(r"[\w.-]+:x:\d+:\d+:", tekst):
            ne("/etc/passwd je otvoren ili pročitan (polje %s)" % gde)
    if not re.search(r"not found|openin_any|Not reading", g["poruka"] + "\n" + log):
        ne("nema poruke o nepostojećem fajlu ili openin_any zabrani: %r" % g["poruka"])
    print(g["poruka"])
elif mod == "poruka":
    g, log = neuspeh_sa_greskom()
    if g["poruka"] != sys.argv[3]:
        ne("poruka je %r, očekivano %r" % (g["poruka"], sys.argv[3]))
    print(g["poruka"])
elif mod == "odbijeno":
    if not isinstance(o, dict) or not isinstance(o.get("greska"), str):
        ne("telo odbijenog zahteva nije {\"greska\": \"...\"}: %r" % (o,))
    print(o["greska"])
else:
    ne("nepoznat mod " + mod)
'

# Preambula iz primer.tex mora biti ista kao podrazumevana u backendu, da smoke dokazuje da ona radi
PY_PREAMBULA='
import re, sys
java, primer = sys.argv[1:3]
with open(java, encoding="utf-8") as f:
    izvor = f.read()
def literali(s):
    return [re.sub(r"\\(.)", r"\1", x) for x in re.findall(r"\"((?:[^\"\\]|\\.)*)\"", s)]
omot = re.search(r"OMOT\s*=\s*(\"(?:[^\"\\]|\\.)*\")\s*;", izvor)
blok = re.search(r"PODRAZUMEVANA_PREAMBULA\s*=\s*String\.join\(\"\\n\",(.*?)\);", izvor, re.S)
if not omot or not blok:
    print("ne nalazim OMOT ili PODRAZUMEVANA_PREAMBULA u " + java)
    sys.exit(1)
ocekivano = literali(omot.group(1)) + literali(blok.group(1)) + ["\\begin{document}"]
with open(primer, encoding="utf-8") as f:
    linije = f.read().split("\n")[:len(ocekivano)]
if linije != ocekivano:
    print("primer.tex ne počinje omotom i podrazumevanom preambulom iz LatexDokument.java")
    for i, (a, b) in enumerate(zip(linije, ocekivano), 1):
        if a != b:
            print("  linija %d: %r, očekivano %r" % (i, a, b))
    sys.exit(1)
'

zahtev() { python3 -I -c "$PY_ZAHTEV" "$@"; }
proveri() { python3 -I -c "$PY_PROVERA" "$@"; }
# Broj redova "prevođenje:" u logu kontejnera (jedan po pdflatex prevođenju, ma koliko formata)
prevodjenja() { docker logs "$IME" 2>&1 | grep -c -F 'prevođenje:' || true; }

# posalji <telo> <odgovor> [dodatni curl argumenti]: POST /render, ispiše HTTP status (000 kad veza pukne)
posalji() {
  local telo="$1" odgovor="$2"
  shift 2
  curl -sS -o "$odgovor" -w '%{http_code}' --max-time 120 -H 'Content-Type: application/json' \
    --data-binary "@$telo" "$@" "$URL/render" 2>"$TMP/curl.err" || true
}

ocekuj_status() {  # ocekuj_status <dobijeno> <očekivano> <opis>
  [[ "$1" == "$2" ]] || pad "$3: HTTP $1, očekivano $2 ($(head -c 300 "$TMP/curl.err" 2>/dev/null))"
}

python3 -I -c "$PY_PREAMBULA" "$JAVA_DOKUMENT" "$TEST_DIR/primer.tex" || pad "preambula u primer.tex"
uspeh "primer.tex nosi omot i podrazumevanu preambulu iz LatexDokument.java"

echo "== docker build $IME"
docker build -t "$IME" "$SERVIS_DIR"

docker rm -f "$IME" >/dev/null 2>&1 || true
# Bez --network none: ne ide uz -p. Bez --rm: posle pada kontejnera docker logs još radi; trap ga briše.
docker run -d --read-only --tmpfs /tmp:size=256m,mode=1777 --cap-drop ALL --security-opt no-new-privileges:true \
  --memory 1g --cpus 1 --pids-limit 128 -p 127.0.0.1:18800:8800 --name "$IME" "$IME" >/dev/null

for _ in $(seq 1 60); do
  if curl -fsS --max-time 2 -o "$TMP/zdravlje" "$URL/zdravlje" 2>/dev/null; then break; fi
  [[ "$(docker inspect -f '{{.State.Running}}' "$IME" 2>/dev/null)" == "true" ]] || pad "kontejner je stao pre /zdravlje"
  sleep 1
done
status=$(curl -sS -o "$TMP/zdravlje" -w '%{http_code}' --max-time 5 "$URL/zdravlje" 2>"$TMP/curl.err" || true)
ocekuj_status "$status" 200 "GET /zdravlje (60 s)"
[[ "$(cat "$TMP/zdravlje")" == "ok" ]] || pad "GET /zdravlje ne vraća ok"
uspeh "GET /zdravlje -> 200 ok"

printf '%s' "$PNG_1X1" | base64 -d >"$TMP/slika.png"

zahtev "$TEST_DIR/primer.tex" svg "$TMP/primer-svg.json" "slika:png:$TMP/slika.png"
status=$(posalji "$TMP/primer-svg.json" "$TMP/o.json")
ocekuj_status "$status" 200 "primer.tex (svg)"
rez=$(proveri uspeh "$TMP/o.json" svg) || pad "primer.tex (svg): $rez"
uspeh "primer.tex -> $rez"

status=$(posalji "$TMP/primer-svg.json" "$TMP/o.json" -H 'Transfer-Encoding: chunked')
ocekuj_status "$status" 200 "primer.tex (svg, chunked telo)"
rez=$(proveri uspeh "$TMP/o.json" svg) || pad "primer.tex (svg, chunked): $rez"
uspeh "primer.tex, telo chunked (kao Java klijent) -> $rez"

zahtev "$TEST_DIR/primer.tex" pdf "$TMP/primer-pdf.json" "slika:png:$TMP/slika.png"
status=$(posalji "$TMP/primer-pdf.json" "$TMP/o.json")
ocekuj_status "$status" 200 "primer.tex (pdf)"
rez=$(proveri uspeh "$TMP/o.json" pdf) || pad "primer.tex (pdf): $rez"
uspeh "primer.tex -> $rez"

pre=$(prevodjenja)
zahtev "$TEST_DIR/primer.tex" svg,png "$TMP/primer-svg-png.json" "slika:png:$TMP/slika.png"
status=$(posalji "$TMP/primer-svg-png.json" "$TMP/o.json")
ocekuj_status "$status" 200 "primer.tex (formati svg,png)"
rez=$(proveri formati "$TMP/o.json" svg,png) || pad "primer.tex (formati svg,png): $rez"
# zabelezi piše red pre odgovora, ali docker logs ga čita iz pipe-a sa malim kašnjenjem
for _ in $(seq 1 20); do
  posle=$(prevodjenja)
  (( posle > pre )) && break
  sleep 0.1
done
(( posle == pre + 1 )) || pad "formati svg,png: $((posle - pre)) novih redova 'prevođenje:' u logu, očekivan tačno 1"
uspeh "primer.tex, formati svg,png -> jedno prevođenje: $rez"

zahtev "$TEST_DIR/primer.tex" png,pdf "$TMP/primer-png-pdf.json" "slika:png:$TMP/slika.png"
status=$(posalji "$TMP/primer-png-pdf.json" "$TMP/o.json")
ocekuj_status "$status" 200 "primer.tex (formati png,pdf)"
rez=$(proveri formati "$TMP/o.json" png,pdf) || pad "primer.tex (formati png,pdf): $rez"
uspeh "primer.tex, formati png,pdf (bez svg) -> $rez"

linija=$(grep -n '\\foo' "$TEST_DIR/primer-greska.tex" | cut -d: -f1)
zahtev "$TEST_DIR/primer-greska.tex" svg "$TMP/greska.json"
status=$(posalji "$TMP/greska.json" "$TMP/o.json")
ocekuj_status "$status" 200 "primer-greska.tex"
rez=$(proveri greska "$TMP/o.json" "$linija") || pad "primer-greska.tex: $rez"
uspeh "primer-greska.tex -> ok=false, $rez"

zahtev "$TEST_DIR/zabranjeno.tex" svg "$TMP/zabranjeno.json"
status=$(posalji "$TMP/zabranjeno.json" "$TMP/o.json")
ocekuj_status "$status" 200 "zabranjeno.tex"
rez=$(proveri zabranjeno "$TMP/o.json") || pad "zabranjeno.tex: $rez"
docker exec "$IME" sh -c 'test ! -e /tmp/x' || pad "zabranjeno.tex: \\write18 je napravio /tmp/x u kontejneru"
uspeh "zabranjeno.tex -> ok=false bez izlaza shell komande i bez /etc/passwd: $rez"

zahtev "$TEST_DIR/bez-strana.tex" pdf "$TMP/bez-strana.json"
status=$(posalji "$TMP/bez-strana.json" "$TMP/o.json")
ocekuj_status "$status" 200 "bez-strana.tex"
rez=$(proveri poruka "$TMP/o.json" "Dokument nema stranica.") || pad "bez-strana.tex: $rez"
uspeh "bez-strana.tex (sam napiše dokument.pdf) -> ok=false: $rez"

ostatak=$(docker exec "$IME" sh -c 'ls -A /tmp' | grep -vxE 'texmf-var|texmf-config' || true)
[[ -z "$ostatak" ]] || pad "radni direktorijumi nisu obrisani posle zahteva: $ostatak"
uspeh "posle zahteva u /tmp nema radnih direktorijuma"

printf '{nije json' >"$TMP/los.json"
status=$(posalji "$TMP/los.json" "$TMP/o.json")
ocekuj_status "$status" 400 "neispravan JSON"
rez=$(proveri odbijeno "$TMP/o.json") || pad "neispravan JSON: $rez"
printf '{"format":"png","tex":"x"}' >"$TMP/los.json"
status=$(posalji "$TMP/los.json" "$TMP/o.json")
ocekuj_status "$status" 400 "format png"
printf '{"format":"svg","tex":"x","prilozi":[{"ime":"../slika","ekstenzija":"png","sadrzaj":""}]}' >"$TMP/los.json"
status=$(posalji "$TMP/los.json" "$TMP/o.json")
ocekuj_status "$status" 400 "ime priloga sa putanjom"
printf '{"format":"svg","tex":"x","prilozi":[{"ime":"slika","ekstenzija":"webp","sadrzaj":""}]}' >"$TMP/los.json"
status=$(posalji "$TMP/los.json" "$TMP/o.json")
ocekuj_status "$status" 400 "WebP prilog"
python3 -I -c 'import json; print(json.dumps({"format": "svg", "tex": "x", "prilozi": [
    {"ime": "s%d" % i, "ekstenzija": "png", "sadrzaj": ""} for i in range(21)]}))' >"$TMP/los.json"
status=$(posalji "$TMP/los.json" "$TMP/o.json")
ocekuj_status "$status" 400 "21 prilog"
python3 -I -c 'print("[" * 100000 + "]" * 100000)' >"$TMP/los.json"
status=$(posalji "$TMP/los.json" "$TMP/o.json")
ocekuj_status "$status" 400 "preduboko ugnežđen JSON"
for formati in '[]' '["svg","svg"]' '["svg","gif"]' '"svg"' '[["svg"]]'; do
  printf '{"format":"svg","formati":%s,"tex":"x"}' "$formati" >"$TMP/los.json"
  status=$(posalji "$TMP/los.json" "$TMP/o.json")
  ocekuj_status "$status" 400 "formati $formati"
done
uspeh "neispravan JSON, format png, ime priloga ../slika, WebP prilog, 21 prilog, dubok JSON, formati prazni, ponovljeni, nepoznati ili bez liste -> 400 ($rez)"

head -c $((70 * 1024 * 1024)) /dev/zero >"$TMP/veliko.json"
status=$(posalji "$TMP/veliko.json" "$TMP/o.json")
ocekuj_status "$status" 413 "telo od 70 MB"
uspeh "telo od 70 MB -> 413"

status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 "$URL/nepostoji" 2>"$TMP/curl.err" || true)
ocekuj_status "$status" 404 "GET /nepostoji"
status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 "$URL/render" 2>"$TMP/curl.err" || true)
ocekuj_status "$status" 405 "GET /render"
uspeh "GET /nepostoji -> 404, GET /render -> 405"

echo "SMOKE OK"
