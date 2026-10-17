"""Render servis gfs-latex (spec banke 3.1): POST /render pretvara ceo LaTeX dokument u SVG ili PDF, GET /zdravlje.

Samo Python stdlib. Svaki zahtev dobija svoj radni direktorijum u /tmp (tmpfs u kontejneru), koji se briše posle
odgovora. pdflatex radi bez shell escape-a, sa openin_any/openout_any=p (bez apsolutnih putanja, ../ i skrivenih
fajlova), bez mktex* skripti i sa ograničenjima procesa (prlimit: CPU, memorija, veličina fajla); pdftocairo pravi SVG
(glifovi kao putanje, bez skripti). Ovo je samo prvi sloj: prava granica je kontejner (read-only, bez mreže, bez
capability-ja). Izvor dokumenta se nikad ne loguje i ne vraća u odgovoru.
"""
import base64
import binascii
import json
import math
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


MIN_TIMEOUT = 5


def _broj_iz_okruzenja(ime, podrazumevano, najmanje, najvise=None):
    """Ceo broj iz env-a u [najmanje, najvise]; inače poruka na stderr i izlaz 2 (kontejner se ne pokreće pogrešno)."""
    vrednost = os.environ.get(ime, "").strip()
    if not vrednost:
        return podrazumevano
    broj = int(vrednost) if vrednost.isdigit() else None
    if broj is None or broj < najmanje or (najvise is not None and broj > najvise):
        opseg = "od %d do %d" % (najmanje, najvise) if najvise is not None else "najmanje %d" % najmanje
        sys.stderr.write("gfs-latex: %s=%r, a mora biti ceo broj %s\n" % (ime, vrednost, opseg))
        sys.exit(2)
    return broj


PORT = _broj_iz_okruzenja("RENDER_PORT", 8800, 1, 65535)
MAX_TIMEOUT = _broj_iz_okruzenja("RENDER_MAX_TIMEOUT", 60, MIN_TIMEOUT)
MAX_IZLAZ = _broj_iz_okruzenja("RENDER_MAX_OUTPUT", 5 * 1024 * 1024, 64 * 1024)
MAX_TELO = _broj_iz_okruzenja("RENDER_MAX_BODY", 60 * 1024 * 1024, 1024 * 1024)

PODRAZUMEVANI_TIMEOUT = 20
SVG_TIMEOUT = 20
# pdftocairo deli rok kompajliranja uz ovu rezervu: ceo odgovor stiže pre nego što Java klijent odustane (rok + 10 s)
SVG_REZERVA = 5
MAX_TEX = 2 * 1024 * 1024
ISTOVREMENO = threading.BoundedSemaphore(4)
NAJVISE_LINIJA_LOGA = 60
NAJVISE_ZNAKOVA_PORUKE = 300
KRAJ_LOGA = 256 * 1024
MEMORIJA_PROCESA = 1024 ** 3
NAJVECI_FAJL = 64 * 1024 ** 2
SOKET_TIMEOUT = 30

FORMATI = frozenset({"svg", "pdf"})
# Samo ono što pdflatex ume da uključi (\includegraphics); WebP nije LaTeX prilog.
EKSTENZIJE = frozenset({"pdf", "png", "jpg", "jpeg"})
NAJVISE_PRILOGA = 20
NAJVISE_CHUNKOVA = 100_000
IME_PRILOGA = re.compile(r"[a-z0-9_-]{1,60}")
DOKUMENT = "dokument"

TEXMFVAR = "/tmp/texmf-var"
TEXMFCONFIG = "/tmp/texmf-config"
OKRUZENJE = {
    "PATH": "/usr/bin:/bin",
    "HOME": "/tmp",
    "LANG": "C.UTF-8",
    "openin_any": "p",
    "openout_any": "p",
    "shell_escape": "f",
    "TEXMFVAR": TEXMFVAR,
    "TEXMFCONFIG": TEXMFCONFIG,
    "TEXMFHOME": "/nonexistent",
    # Log bez preloma na 79 znakova: poruka greške ostaje u jednom redu (i za parsiranje ovde i u backendu).
    "max_print_line": "1000",
    # Bez mktexpk/mktextfm/...: pdflatex ne pokreće skripte ni METAFONT (fontovi su Type1 iz image-a, cm-super).
    "MKTEXPK": "0",
    "MKTEXTFM": "0",
    "MKTEXMF": "0",
    "MKTEXTEX": "0",
    "MKTEXFMT": "0",
}
PDFLATEX = ["pdflatex", "-no-shell-escape", "-interaction=nonstopmode", "-halt-on-error", "-file-line-error",
            "-output-directory", ".", DOKUMENT + ".tex"]

GRESKA_DOKUMENT = re.compile(r"^(?:\./)?dokument\.tex:(\d+): (.*)$", re.M)
GRESKA_DRUGI_FAJL = re.compile(r"^[^\s:]+:\d+: (.*)$", re.M)
GRESKA_UZVICNIK = re.compile(r"^!\s*(\S.*)$", re.M)
# Poruke koje samo kažu da je TeX stao; pravi uzrok je poslednji red "! ..." pre njih (npr. fajl ne postoji).
OPSTE_PORUKE = ("Emergency stop", "==> Fatal error")

NEISPRAVAN = "Neispravan zahtev."
PREVELIK = "Prevelik zahtev."
LOG_KLJUC = threading.Lock()


class OdbijenZahtev(Exception):
    def __init__(self, status, poruka):
        super().__init__(poruka)
        self.status = status
        self.poruka = poruka


def zabelezi(linija):
    with LOG_KLJUC:
        sys.stdout.write(linija + "\n")
        sys.stdout.flush()


# --- zahtev -------------------------------------------------------------------------------------------------------

def procitaj_zahtev(telo):
    """JSON telo -> (format, tex u UTF-8, {fajl: bajtovi}, rok u s); OdbijenZahtev(400) za sve što ne odgovara."""
    try:
        z = json.loads(telo)
    except (ValueError, UnicodeDecodeError, RecursionError):  # RecursionError: preduboko ugnežđen JSON
        raise OdbijenZahtev(400, NEISPRAVAN)
    if not isinstance(z, dict):
        raise OdbijenZahtev(400, NEISPRAVAN)

    format_ = z.get("format")
    tex = z.get("tex")
    if not isinstance(format_, str) or format_ not in FORMATI or not isinstance(tex, str) or not tex.strip():
        raise OdbijenZahtev(400, NEISPRAVAN)
    try:
        tex_bajtovi = tex.encode("utf-8")
    except UnicodeEncodeError:
        raise OdbijenZahtev(400, NEISPRAVAN)
    if len(tex_bajtovi) > MAX_TEX:
        raise OdbijenZahtev(400, NEISPRAVAN)

    timeout = z.get("timeoutSekunde")
    if timeout is None:
        timeout = PODRAZUMEVANI_TIMEOUT
    elif type(timeout) is not int:
        raise OdbijenZahtev(400, NEISPRAVAN)
    timeout = min(max(timeout, MIN_TIMEOUT), MAX_TIMEOUT)

    prilozi = z.get("prilozi")
    if prilozi is None:
        prilozi = []
    if not isinstance(prilozi, list) or len(prilozi) > NAJVISE_PRILOGA:
        raise OdbijenZahtev(400, NEISPRAVAN)
    fajlovi = {}
    for p in prilozi:
        if not isinstance(p, dict):
            raise OdbijenZahtev(400, NEISPRAVAN)
        ime, ekstenzija, sadrzaj = p.get("ime"), p.get("ekstenzija"), p.get("sadrzaj")
        if (not isinstance(ime, str) or not IME_PRILOGA.fullmatch(ime) or not isinstance(ekstenzija, str)
                or ekstenzija not in EKSTENZIJE or not isinstance(sadrzaj, str)):
            raise OdbijenZahtev(400, NEISPRAVAN)
        if ime == DOKUMENT:
            raise OdbijenZahtev(400, "Ime priloga %s je rezervisano." % DOKUMENT)
        fajl = "%s.%s" % (ime, ekstenzija)
        if fajl in fajlovi:
            raise OdbijenZahtev(400, NEISPRAVAN)
        try:
            fajlovi[fajl] = base64.b64decode(sadrzaj, validate=True)
        except (binascii.Error, ValueError):
            raise OdbijenZahtev(400, NEISPRAVAN)
    return format_, tex_bajtovi, fajlovi, timeout


# --- kompajliranje ------------------------------------------------------------------------------------------------

def pokreni(argv, radni, rok, izlaz):
    """Pokreće argv u radnom dir-u sa ograničenjima (CPU = rok, memorija, veličina fajla, bez core-a). stdout i stderr
    idu u fajl, ne u memoriju servera. Po isteku roka ubija celu grupu procesa i baca TimeoutExpired."""
    cpu = max(1, math.ceil(rok))
    komanda = ["prlimit", "--cpu=%d:%d" % (cpu, cpu + 5), "--as=%d" % MEMORIJA_PROCESA,
               "--fsize=%d" % NAJVECI_FAJL, "--core=0", "--"] + argv
    with open(izlaz, "wb") as f:
        proces = subprocess.Popen(komanda, cwd=radni, env=OKRUZENJE, stdin=subprocess.DEVNULL, stdout=f,
                                  stderr=subprocess.STDOUT, start_new_session=True)
    try:
        return proces.wait(timeout=rok)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(proces.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        proces.wait()
        raise


def kraj_fajla(put):
    """Poslednjih KRAJ_LOGA bajtova fajla kao tekst ("" kad fajla nema); log može biti veliki, čita se samo kraj."""
    try:
        with open(put, "rb") as f:
            f.seek(0, os.SEEK_END)
            f.seek(max(0, f.tell() - KRAJ_LOGA))
            return f.read().decode("utf-8", "replace")
    except FileNotFoundError:
        return ""


def pocinje_kao_pdf(put):
    try:
        with open(put, "rb") as f:
            return f.read(5) == b"%PDF-"
    except OSError:
        return False


def poslednje_linije(tekst):
    return "\n".join(tekst.splitlines()[-NAJVISE_LINIJA_LOGA:])


def skrati(poruka):
    return poruka.strip()[:NAJVISE_ZNAKOVA_PORUKE]


def greska_iz_loga(log):
    """(linija u dokumentu ili None, poruka) iz loga pdflatex -file-line-error."""
    for obrazac in (GRESKA_DOKUMENT, GRESKA_DRUGI_FAJL):
        m = obrazac.search(log)
        if m:
            linija = int(m.group(1)) if obrazac is GRESKA_DOKUMENT else None
            poruka = m.group(m.lastindex).strip()
            if poruka.startswith(OPSTE_PORUKE):
                uzroci = GRESKA_UZVICNIK.findall(log, 0, m.start())
                if uzroci:
                    poruka = uzroci[-1]
            return linija, skrati(poruka)
    m = GRESKA_UZVICNIK.search(log)
    if m:
        return None, skrati(m.group(1))
    return None, "LaTeX nije uspeo, vidi log."


def neuspeh(linija, poruka, log):
    return {"ok": False, "greska": {"linija": linija, "poruka": poruka}, "log": poslednje_linije(log)}


def neuspeh_procesa(kod, log, rok):
    """Proces je završio sa kodom != 0: uzrok iz signala (rlimit) ili iz loga."""
    if kod == -signal.SIGXCPU:
        return neuspeh(None, "Prekoračeno vreme kompajliranja (%d s)." % rok, log)
    if kod == -signal.SIGXFSZ:
        return neuspeh(None, "Prevelik izlaz.", log)
    linija, poruka = greska_iz_loga(log)
    return neuspeh(linija, poruka, log)


def renderuj(format_, tex, prilozi, rok):
    """Prevodi dokument (najviše dva prolaza pdflatex-a, drugi samo kad log traži Rerun) i vraća odgovor za JSON."""
    pocetak = time.monotonic()
    kraj_roka = pocetak + rok
    radni = tempfile.mkdtemp(prefix="render-", dir="/tmp")
    try:
        with open(os.path.join(radni, DOKUMENT + ".tex"), "wb") as f:
            f.write(tex)
        for fajl, sadrzaj in prilozi.items():
            with open(os.path.join(radni, fajl), "wb") as f:
                f.write(sadrzaj)
        pdf = os.path.join(radni, DOKUMENT + ".pdf")
        izlaz_tex = os.path.join(radni, "pdflatex.izlaz")
        istek = "Prekoračeno vreme kompajliranja (%d s)." % rok

        def procitaj_log():  # dokument.log; bez njega ono što je pdflatex ispisao (npr. nije ni počeo)
            return kraj_fajla(os.path.join(radni, DOKUMENT + ".log")) or kraj_fajla(izlaz_tex)

        log = ""
        for _ in range(2):
            preostalo = kraj_roka - time.monotonic()
            if preostalo <= 0:
                return neuspeh(None, istek, log)
            try:
                kod = pokreni(PDFLATEX, radni, preostalo, izlaz_tex)
            except subprocess.TimeoutExpired:
                return neuspeh(None, istek, procitaj_log())
            log = procitaj_log()
            if kod != 0:
                return neuspeh_procesa(kod, log, rok)
            # Bez strana pdflatex ne piše PDF, a dokument.pdf je mogao da napiše sam dokument (\openout).
            if "No pages of output" in log or not pocinje_kao_pdf(pdf):
                return neuspeh(None, "Dokument nema stranica.", log)
            if "Rerun to get" not in log:
                break

        izlaz = pdf
        if format_ == "svg":
            izlaz = os.path.join(radni, DOKUMENT + ".svg")
            izlaz_svg = os.path.join(radni, "pdftocairo.izlaz")
            rok_svg = min(SVG_TIMEOUT, max(0.0, kraj_roka - time.monotonic()) + SVG_REZERVA)
            try:
                kod = pokreni(["pdftocairo", "-svg", DOKUMENT + ".pdf", DOKUMENT + ".svg"], radni, rok_svg, izlaz_svg)
            except subprocess.TimeoutExpired:
                return neuspeh(None, "Prekoračeno vreme pretvaranja u SVG.", kraj_fajla(izlaz_svg))
            if kod == -signal.SIGXFSZ:
                return neuspeh(None, "Prevelik izlaz.", kraj_fajla(izlaz_svg))
            if kod != 0 or not os.path.isfile(izlaz) or os.path.getsize(izlaz) == 0:
                return neuspeh(None, "Pretvaranje PDF-a u SVG nije uspelo.", kraj_fajla(izlaz_svg))

        velicina = os.path.getsize(izlaz)
        if velicina > MAX_IZLAZ:
            return neuspeh(None, "Prevelik izlaz.", log)
        if velicina == 0:
            return neuspeh(None, "LaTeX nije uspeo, vidi log.", log)
        with open(izlaz, "rb") as f:
            sadrzaj = base64.b64encode(f.read()).decode("ascii")
        return {"ok": True, format_: sadrzaj, "trajanjeMs": int((time.monotonic() - pocetak) * 1000)}
    finally:
        shutil.rmtree(radni, ignore_errors=True)


# --- HTTP ---------------------------------------------------------------------------------------------------------

class Rukovalac(BaseHTTPRequestHandler):
    # HTTP/1.1 zbog Expect: 100-continue (preveliko telo se odbija pre slanja) i chunked tela (tako šalje Java klijent);
    # svaki odgovor ipak zatvara vezu.
    protocol_version = "HTTP/1.1"
    server_version = "gfs-latex"
    sys_version = ""
    timeout = SOKET_TIMEOUT
    DOZVOLJENO = {"/render": "POST", "/zdravlje": "GET"}

    def parse_request(self):
        self._pocetak = time.monotonic()
        return super().parse_request()

    def handle_expect_100(self):
        if self.command == "POST" and self._putanja() == "/render":
            try:
                duzina = self._duzina_tela()
            except OdbijenZahtev as e:
                self._json(e.status, {"greska": e.poruka})
                return False
            if duzina is not None and duzina > MAX_TELO:
                self._json(413, {"greska": PREVELIK})
                return False
        return super().handle_expect_100()

    def do_GET(self):
        if self._putanja() == "/zdravlje":
            self._odgovori(200, b"ok", "text/plain; charset=utf-8")
        else:
            self._nedozvoljeno()

    def do_POST(self):
        if self._putanja() != "/render":
            self._nedozvoljeno()
            return
        try:
            duzina = self._duzina_tela()
            if duzina is not None and duzina > MAX_TELO:
                raise OdbijenZahtev(413, PREVELIK)
            with ISTOVREMENO:
                telo = self._procitaj_telo(duzina)
                format_, tex, prilozi, rok = procitaj_zahtev(telo)
                del telo
                odgovor = renderuj(format_, tex, prilozi, rok)
        except OdbijenZahtev as e:
            self._json(e.status, {"greska": e.poruka})
            return
        except (ConnectionError, TimeoutError) as e:
            self.close_connection = True
            zabelezi("POST /render veza prekinuta: %s" % type(e).__name__)
            return
        except Exception as e:  # noqa: BLE001 - klijent dobija 500 bez detalja, log samo tip greške
            zabelezi("POST /render greška servisa: %s" % type(e).__name__)
            self._json(500, {"greska": "Greška servisa."})
            return
        self._json(200, odgovor, "ok=%s" % ("true" if odgovor["ok"] else "false"))

    def __getattr__(self, ime):
        # Svaki drugi metod (HEAD, PUT, i nestandardni) je 405 ili 404, a ne 501 iz stdlib-a.
        if ime.startswith("do_"):
            return self._nedozvoljeno
        raise AttributeError(ime)

    def _putanja(self):
        return urlsplit(self.path).path

    def _duzina_tela(self):
        """Content-Length ili None za chunked telo; OdbijenZahtev(400) kad zaglavlja ne opisuju telo jednoznačno."""
        prenos = self.headers.get_all("Transfer-Encoding") or []
        duzine = self.headers.get_all("Content-Length") or []
        if prenos:
            if duzine or len(prenos) != 1 or prenos[0].strip().lower() != "chunked":
                raise OdbijenZahtev(400, NEISPRAVAN)
            return None
        if len(duzine) != 1 or not re.fullmatch(r"\d{1,15}", duzine[0].strip()):
            raise OdbijenZahtev(400, NEISPRAVAN)
        return int(duzine[0])

    def _procitaj_telo(self, duzina):
        if duzina is not None:
            telo = self.rfile.read(duzina)
            if len(telo) != duzina:
                raise OdbijenZahtev(400, NEISPRAVAN)
            return telo
        telo = bytearray()  # chunked: jedan bafer koji raste, bez liste delova i spajanja na kraju
        for _ in range(NAJVISE_CHUNKOVA + 1):
            red = self.rfile.readline(1026)
            velicina = red.split(b";", 1)[0].strip()
            if not red.endswith(b"\n") or not re.fullmatch(rb"[0-9a-fA-F]{1,15}", velicina):
                raise OdbijenZahtev(400, NEISPRAVAN)
            velicina = int(velicina, 16)
            if velicina == 0:
                break
            if len(telo) + velicina > MAX_TELO:
                raise OdbijenZahtev(413, PREVELIK)
            deo = self.rfile.read(velicina)
            if len(deo) != velicina or self.rfile.readline(3) not in (b"\r\n", b"\n"):
                raise OdbijenZahtev(400, NEISPRAVAN)
            telo += deo
        else:
            raise OdbijenZahtev(400, NEISPRAVAN)  # više od NAJVISE_CHUNKOVA delova
        for _ in range(64):  # trailer zaglavlja do prazne linije
            if self.rfile.readline(8194) in (b"\r\n", b"\n", b""):
                return telo
        raise OdbijenZahtev(400, NEISPRAVAN)

    def _nedozvoljeno(self):
        dozvoljen = self.DOZVOLJENO.get(self._putanja())
        if dozvoljen is None:
            self._json(404, {"greska": "Nepostojeća putanja."})
        else:
            self._json(405, {"greska": "Metod nije dozvoljen."}, zaglavlja={"Allow": dozvoljen})

    def _json(self, status, telo, napomena="", zaglavlja=None):
        podaci = json.dumps(telo, ensure_ascii=False).encode("utf-8")
        self._odgovori(status, podaci, "application/json; charset=utf-8", napomena, zaglavlja)

    def _odgovori(self, status, podaci, tip, napomena="", zaglavlja=None):
        self.close_connection = True
        try:
            self.send_response(status)
            self.send_header("Content-Type", tip)
            self.send_header("Content-Length", str(len(podaci)))
            self.send_header("Connection", "close")
            for ime, vrednost in (zaglavlja or {}).items():
                self.send_header(ime, vrednost)
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(podaci)
        except OSError:
            napomena = (napomena + " veza prekinuta").strip()
        trajanje = int((time.monotonic() - self._pocetak) * 1000)
        putanja = re.sub(r"[^\x21-\x7e]", "?", self._putanja())[:200]
        zabelezi(" ".join(filter(None, (self.command, putanja, str(status), "%dms" % trajanje, napomena))))

    def log_request(self, code="-", size="-"):
        pass  # jedna linija po zahtevu ide iz _odgovori

    def log_message(self, format, *args):  # noqa: A002 - potpis iz BaseHTTPRequestHandler
        zabelezi("http: " + (format % args))


def main():
    os.makedirs(TEXMFVAR, exist_ok=True)
    os.makedirs(TEXMFCONFIG, exist_ok=True)
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Rukovalac)
    server.daemon_threads = True
    # PID 1 u kontejneru: bez rukovaoca SIGTERM se ignoriše i docker stop čeka 10 s
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    zabelezi("gfs-latex sluša na :%d (timeout <= %d s, izlaz <= %d B, telo <= %d B)"
             % (PORT, MAX_TIMEOUT, MAX_IZLAZ, MAX_TELO))
    try:
        server.serve_forever()
    except (KeyboardInterrupt, SystemExit):
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
