# gfs-latex: render servis za LaTeX

Mali HTTP servis koji ceo LaTeX dokument pretvara u SVG (telefon, projektor) ili PDF, za banku zadataka (spec banke 3.1).
Zove ga backend (`service/banka/LatexRender`, `gfs.latex.url`); sam ne zna ništa o zadacima ni bazi. Debian bookworm +
TeX Live iz Debiana + poppler (`pdftocairo`); server je Python 3 stdlib (`server.py`), bez pip zavisnosti i bez tajni.

## API

### `POST /render`

Telo je JSON do `RENDER_MAX_BODY` (60 MB), sa `Content-Length` ili `Transfer-Encoding: chunked` (Java klijent šalje chunked):

```json
{
  "format": "svg",
  "tex": "\\documentclass{standalone}\n\\usepackage{graphicx}\n\\begin{document}\n$x^2$ \\includegraphics[width=2cm]{dijagram}\n\\end{document}\n",
  "prilozi": [{"ime": "dijagram", "ekstenzija": "png", "sadrzaj": "<base64>"}],
  "timeoutSekunde": 20
}
```

- `format`: `svg` ili `pdf`. `tex`: ceo dokument (omot, preambula i telo sklapa backend, `LatexDokument`), UTF-8, do 2 MB.
- `prilozi` (može da izostane, najviše 20): svaki postaje fajl `<ime>.<ekstenzija>` pored dokumenta, pa ga LaTeX zove
  sa `\includegraphics{<ime>}`. `ime` po `^[a-z0-9_-]{1,60}$` (osim `dokument`, rezervisano), `ekstenzija` je `pdf`,
  `png`, `jpg` ili `jpeg`, `sadrzaj` je base64, isti fajl dvaput je 400. WebP nije LaTeX prilog (pdflatex ga ne
  uključuje), pa je `webp` 400.
- `timeoutSekunde` (može da izostane, podrazumevano 20): rok za ceo prevod, sveden na 5 do `RENDER_MAX_TIMEOUT`.

Odgovor je uvek 200 kad je zahtev ispravan:

```json
{"ok": true, "svg": "<base64>", "trajanjeMs": 640}
{"ok": false, "greska": {"linija": 14, "poruka": "Undefined control sequence."}, "log": "<poslednjih 60 linija loga>"}
```

- Uspeh uvek nosi traženi format (`svg` ili `pdf`, neprazan), neuspeh uvek nosi `greska` (i `log`).
- `greska.linija` je linija u **celom dokumentu** (backend oduzima linije omota i preambule) ili `null` kad se ne zna.
  Poruka je prvi red `dokument.tex:N: ...` iz loga (`-file-line-error`), pa red iz drugog fajla (paket) bez linije, pa
  prvi red `! ...`; za `Emergency stop.` uzima se red `! ...` pre njega (npr. `LaTeX Error: File ... not found.`).
  Najviše 300 znakova.
- Poruke servisa: `Prekoračeno vreme kompajliranja (N s).`, `Prevelik izlaz.` (izlaz iznad `RENDER_MAX_OUTPUT` ili
  fajl iznad 64 MB), `Dokument nema stranica.` (nema strana, ili `dokument.pdf` nije PDF koji je napravio pdflatex, npr.
  dokument ga sam napiše kroz `\openout`), `Pretvaranje PDF-a u SVG nije uspelo.`,
  `Prekoračeno vreme pretvaranja u SVG.`, `LaTeX nije uspeo, vidi log.`

Ostali statusi (telo `{"greska": "..."}`): **400** `Neispravan zahtev.` (nije JSON objekat ili je preduboko ugnežđen,
pogrešan tip polja, `format`, prazan ili prevelik `tex`, neispravan prilog ili više od 20 priloga, nejasna
`Content-Length`/`Transfer-Encoding` zaglavlja, chunked telo u više od 100 000 delova), **413** `Prevelik zahtev.` (telo
iznad `RENDER_MAX_BODY`; uz `Expect: 100-continue` odbija se pre slanja tela), **404** nepoznata putanja, **405**
pogrešan metod (zaglavlje `Allow`), **500** `Greška servisa.` (npr. pun `/tmp`).

### `GET /zdravlje`

200, tekst `ok`. Koriste ga `HEALTHCHECK` image-a i `LatexRender.zdrav()`.

## Kako prevodi

- Po zahtevu radni direktorijum `mkdtemp` u `/tmp`: `dokument.tex` i prilozi; briše se u `finally`, i posle greške.
- `pdflatex -no-shell-escape -interaction=nonstopmode -halt-on-error -file-line-error -output-directory . dokument.tex`,
  sa okruženjem `openin_any=p openout_any=p shell_escape=f TEXMFVAR=/tmp/texmf-var TEXMFCONFIG=/tmp/texmf-config
  TEXMFHOME=/nonexistent HOME=/tmp PATH=/usr/bin:/bin`, `max_print_line=1000` (poruka greške u jednom redu) i
  `MKTEXPK=MKTEXTFM=MKTEXMF=MKTEXTEX=MKTEXFMT=0` (bez mktex skripti i METAFONT-a; EC fontovi su Type1 iz `cm-super`).
  Zato fontovi koji u TeX Live-u postoje samo kao METAFONT izvor (bez Type1) ne rade: prevod pada sa `Font ... not found`.
- Ograničenja procesa kroz `prlimit` (bez `preexec_fn`, koji nije bezbedan uz niti): CPU = preostali rok (tvrdo + 5 s),
  1 GB adresnog prostora, 64 MB po fajlu, bez core fajla. Po isteku roka ubija se cela grupa procesa.
- Drugi prolaz samo kad log traži (`Rerun to get ...`), u istom roku. stdout pdflatex-a ide u fajl, ne u memoriju servera.
  Posle prolaza `dokument.pdf` mora da počinje sa `%PDF-`, a log ne sme da kaže `No pages of output`.
- SVG: `pdftocairo -svg dokument.pdf dokument.svg` (glifovi kao putanje, slike kao `data:` URI, bez fontova i skripti),
  najviše 20 s i ne više od 5 s posle roka prevoda, pa odgovor stiže pre nego što Java klijent odustane (rok + 10 s).
- Najviše 4 prevoda istovremeno (`BoundedSemaphore`); peti čeka. `/zdravlje` ne čeka.
- Jedna linija loga po zahtevu na stdout (metod, putanja, status, ms, `ok=`); izvor dokumenta se nikad ne loguje.

## Izolacija

Servis je prvi sloj; deny-lista u backendu (`LatexPravila`) je samo rana pomoć. Prava granica je kontejner. Compose
(wrapper repo GFS-deploy, servis `latex`, kontejner `gfs-latex` i `gfs-staging-latex`) ga pokreće sa: mreža
`gfs-latex-net` sa `internal: true` (samo backend i latex, bez izlaza na internet), `read_only: true`,
`tmpfs: /tmp:size=256m,mode=1777`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, `mem_limit: 1g`,
`cpus: 1.0`, `pids_limit: 128`, bez `volumes`, bez `env_file`, bez portova na hostu (`GFS_LATEX_URL=http://latex:8800`).

- **Može:** da čita TeX Live stablo i fajlove svog radnog direktorijuma, da piše u svoj radni direktorijum (u `/tmp`),
  da troši CPU i memoriju do rokova i limita gore.
- **Ne može:** da pokrene shell komandu (`\write18`, `\immediate\write18` se samo zapiše u log kao `disabled`), da otvori
  apsolutnu putanju, `../` ili skriven fajl (`\input{/etc/passwd}` je `File not found`), da pokrene mktex skripte, da
  izađe na mrežu, da piše van `/tmp`, da dobije nove privilegije; radi kao `render` (uid 1000), bez capability-ja.
- U image-u nema tajni ni podataka: samo TeX Live, poppler, Python i `server.py` (javni repo). Kontejner ne vidi bazu ni
  `gfs.mediji.dir`; prilozi stižu u zahtevu.

## Lokalno

```bash
bash latex-render/test/smoke.sh   # build + kontejner sa izolacijom kao u compose-u + provere API-ja (isto radi CI)
```

Smoke proverava: `primer.tex` (omot i podrazumevana preambula iz `LatexDokument.java`, TikZ automat, `enumitem`,
`booktabs`, `amsmath`/`amssymb`, PNG prilog) u SVG i PDF, isto telo poslato chunked, `primer-greska.tex` (`ok=false`,
linija 14 = treća linija tela), `zabranjeno.tex` (`ok=false`, nema izlaza `id` ni sadržaja `/etc/passwd`, nema `/tmp/x`
u kontejneru), `bez-strana.tex` (sam napiše `dokument.pdf`: `Dokument nema stranica.`), da posle zahteva u `/tmp` nema
radnih direktorijuma, 400 (loš i preduboko ugnežđen JSON, `format: png`, ime `../slika`, WebP prilog, 21 prilog), 413
(70 MB), 404, 405 i `/zdravlje`.

Ručno, na portu na koji backend sa `dev` profilom već gleda (`gfs.latex.url` = `http://localhost:8800`):

```bash
docker build -t gfs-latex latex-render
docker run --rm --read-only --tmpfs /tmp:size=256m,mode=1777 --cap-drop ALL \
  --security-opt no-new-privileges:true -p 127.0.0.1:8800:8800 gfs-latex
python3 -I -c 'import json; print(json.dumps({"format": "pdf", "tex": open("latex-render/test/primer-greska.tex").read()}))' \
  | curl -sS -H 'Content-Type: application/json' --data-binary @- http://127.0.0.1:8800/render
```

## Image

Oko 1,3 GB raspakovan (oko 400 MB kompresovan), build oko 3 minuta (TeX Live iz Debiana; `texlive-latex-extra` povlači i
`libpdfbox-java`, ali bez JRE-a jer preporuke nisu uključene). Gradi se retko, slojevi se keširaju.

## Promenljive okruženja

| Promenljiva | Podrazumevano | Značenje |
|---|---|---|
| `RENDER_PORT` | `8800` | port servera (sluša na `0.0.0.0` u kontejneru; i healthcheck ga čita), 1 do 65535 |
| `RENDER_MAX_TIMEOUT` | `60` | gornja granica za `timeoutSekunde` (s), najmanje 5 |
| `RENDER_MAX_OUTPUT` | `5242880` | najveći SVG ili PDF u odgovoru (B), iznad je `Prevelik izlaz.`; najmanje 65536 |
| `RENDER_MAX_BODY` | `62914560` | najveće telo zahteva (B), iznad je 413; najmanje 1048576 |

Vrednost van opsega ili koja nije ceo broj: server ispiše poruku na stderr i izađe sa kodom 2 (kontejner ne radi pogrešno
podešen).
