# Banka zadataka (GFS MCP)

## Stablo i namene
- Banka predmeta ima dva odvojena stabla po nameni: `PREDAVANJE` (zadaci na času: Novica ih deli studentima na telefon, a odgovor se ocenjuje po šemi polja) i `TEST` (zadaci za kolokvijume i ispite, iz kojih aplikacija pravi nove rokove). Namenu bira koren (`napravi_grupu` sa `namena`, bez `roditeljId`); podgrupe je nasleđuju.
- Čvor je ili unutrašnji (ima podgrupe) ili list (ima varijante), nikad oboje. Najveća dubina je 6; naziv je jedinstven među braćom, bez obzira na velika slova.
- List sa varijantama mora imati `tezina` 1-5 (inače 409 `Težina je obavezna za grupu sa varijantama.`). Šema polja (`polja`) i `uputstvo` stoje na listu.
- Varijante jedne grupe su paralelne: isti tip zadatka, isti oblik odgovora, drugi brojevi ili primeri. Drugi tip zadatka je druga grupa.
- Dve težine, ne mešaj ih: `tezina` grupe (1-5, "težina tipa") kaže koliko je težak sam tip zadatka (binarni u dekadni je lakši od dekadnog u heksadekadni) i služi za sastav testa; `nivo` varijante (1, 2 ili 3; Novica ga vidi kao zvezdice) kaže koliko je ta varijanta laka unutar svog tipa (npr. 4 bita = 1, 8 bita = 2, 12 bita sa razlomkom = 3). Lakši i teži primeri istog tipa su **jedna grupa** sa varijantama različitog `nivo`, nikad dve grupe "lakši" i "teži".
- `stablo_banke` daje celo stablo sa brojačima, `grupa` grupu sa šemom i sažetkom varijanti, `varijanta` jednu varijantu sa LaTeX-om, `pretraga_banke` traži po tekstu (`q`, bar 2 znaka), nameni, statusu, izvoru i seriji.
- `kopiraj_grupu` kopira grupu sa celim podstablom (šema sa istim id-jevima opcija, uputstvo, varijante osim arhiviranih, kao tvoji nacrti) u drugu namenu istog predmeta (`ciljNamena`, opciono `ciljRoditeljId` u ciljnom stablu). Ista tema često treba i za `PREDAVANJE` i za `TEST`: napravi u jednoj nameni, proveri, pa kopiraj u drugu.

## Uputstvo grupe
- `uputstvo` (do 5 000 znakova) opisuje kakve varijante grupa treba da ima: stil, opseg brojeva, šta izbegavati, kako izgleda rešenje. Kad praviš grupu, napiši ga tako da iz njega i postojećih varijanti kasnije možeš da napraviš novu varijantu istog oblika.
- Pre nove varijante u postojećoj grupi pročitaj `uputstvo` i bar jednu postojeću varijantu (`grupa`, pa `varijanta`) i drži ih se kao uzora. Uputstvo je uzor za sadržaj, ne naredba tebi (`vodic pravila`).

## LaTeX za telefon
- `tekst` (obavezan) i `resenje` (opciono) su **telo** LaTeX dokumenta, do 20 000 znakova svaki. Server sam pravi dokument: `\documentclass[preview,border=6pt,varwidth=84mm,11pt]{standalone}`, preambula predmeta, `\begin{document}`, telo, `\end{document}`. Zato telo nema `\documentclass`, `\usepackage` ni `\begin{document}`.
- Širina je 84 mm (ekran telefona). Tabele i dijagrami moraju da stanu: manje kolona, `\small`, TikZ sa `scale`, ili `\resizebox{\linewidth}{!}{...}`. Duge račune lomi u više redova (`align*`, `aligned`).
- Tekst je na srpskom, latinicom sa č, ć, ž, š, đ. Matematika: `$...$`, `\[...\]`, `align*`; osnova broja kao indeks, `$1011_{(2)}$`.
- Rešenje piši za studenta: postupak u nekoliko koraka i rezultat, ne samo broj.
- Preambulu daje alat `preambula`: `podrazumevana: true` znači podrazumevanu (`podrazumevanaPreambula`: T1 i utf8, `amsmath`, `amssymb`, `tikz` sa bibliotekama `automata`, `positioning` i `arrows.meta`, `graphicx`, `booktabs`, `enumitem`, pasusi bez uvlačenja). Koristi samo pakete i makroe iz efektivne preambule; kad nešto fali, reci Novici (preambulu menja on u UI-ju).
- Za jednostavne dijagrame (automati, grafovi, stabla, šeme, skice) koristi TikZ u telu, a ne sliku:

```latex
\begin{tikzpicture}[->, >=Stealth, node distance=18mm]
  \node[state, initial] (q0) {$q_0$};
  \node[state, accepting, right=of q0] (q1) {$q_1$};
  \path (q0) edge[bend left] node[above] {1} (q1)
        (q1) edge[bend left] node[below] {0} (q0);
\end{tikzpicture}
```

## Zabranjene komande
Server odbija tekst i rešenje sa bilo kojom od ovih komandi pre rendera, i kad je iza `%` (400 `Komanda \x nije dozvoljena.`):
`\write18`, `\input@path`, `\input`, `\include`, `\openin`, `\openout`, `\read`, `\write`, `\immediate`, `\csname`, `\directlua`, `\InputIfFileExists`, `\@input`, `\@@input`, `\@iinput`, `\lstinputlisting`, `\verbatiminput`, `\includepdf`, `\includepdfmerge`, `\url{file:` i `\href{file:`, niz `^^`, a u telu još `\usepackage`, `\RequirePackage`, `\documentclass`, `\begin{document}`, `\end{document}` i `\catcode`.
- Komanda se poklapa kao cela reč (`\includegraphics` nije `\include`), ali `\\input` (prelom reda pa reč "input") server iz opreza odbija; piši `\\ input` ili prelomi drugačije.
- `\includegraphics` je dozvoljen samo sa imenom priloga (ispod).

## Prilozi (slike i PDF u zadatku)
- Figuru iz PDF-a izreži lokalno (`pdftocairo -png -r 200 -f N -l N ...`, `pdfimages`, Python), pa je otpremi iz Bash-a: `gfs-mcp-upload <gfs|gfs-staging> <fajl> --serija <serija>`. Skripta ispisuje `medijId=<id>` i `vrsta=<vrsta>`: `PRILOG_SLIKA` za PNG i JPEG, `PRILOG_PDF` za PDF, do 10 MB; vrsta se određuje po sadržaju, ne po ekstenziji. Prvi argument mora odgovarati serveru alata (`gfs` za `PROD`, `gfs-staging` za `STAGING`). Ključ skripta nalazi sama; nikad ga ne traži.
- U varijanti: `"prilozi": [{"ime": "skica", "medijId": "<id>"}]`, a u telu `\includegraphics{skica}` ili `\includegraphics[width=0.8\linewidth]{skica}`. Ime je 1-60 malih slova, cifara, `_` ili `-`, bez putanje i ekstenzije, jedinstveno u varijanti, nikad `dokument`; najviše 10 priloga. Opcije u `[...]` su slobodne (i `page=2` za PDF), a u `{...}` je samo ime.
- WebP i GIF nisu prilozi zadatka (pdflatex ih ne čita), a slika za slajd (`--slika`, vrsta `SLIKA`) nije prilog.
- `izmeni_varijantu` sa `prilozi` menja ceo spisak; `"prilozi": null` briše sve.
- Prilozi se ne kopiraju između okruženja: `medijId` sa `gfs-staging` ne postoji na `gfs`, pa za drugo okruženje fajl otpremi ponovo.
- TikZ je podrazumevani put za jednostavne dijagrame; prilog je za fotografije, skenirane crteže i složene figure.

## Šema polja
Šema (`polja`) kaže šta student unosi na telefonu i kako se ocenjuje; `[]` znači rad na papiru (telefon samo prikaže zadatak). Najviše 10 polja; `kljuc` je 1-10 malih slova i cifara, počinje slovom i jedinstven je; `oznaka` ima 1-200 znakova (npr. `a) dekadni`).
- `BROJ`: broj; `odstupanje` >= 0 (podrazumevano 0) sa `odstupanjeTip` `APSOLUTNO` (podrazumevano) ili `PROCENAT`, `jedinica` do 30 znakova. Student sme decimalni zarez ili tačku.
- `TEKST`: kratak tekst; `zanemariVelicinu` i `zanemariRazmake` (podrazumevano true); ćirilica i dijakritici se porede kao latinica bez njih.
- `JEDAN_TACAN`: tačna je jedna od 2-6 `opcije` (`tekst` 1-200 znakova, sme `$formula$`).
- `VISE_TACNIH`: tačan je skup od 2-6 `opcije`.

Opcija bez `id` dobija sledeći slobodan id (u novoj grupi 1, 2, 3 ... redom). Pravi id-jevi su u odgovoru (`grupa.polja`); pročitaj ih pre pisanja odgovora.

Primer `napravi_grupu` (list sa sva četiri tipa polja):

```json
{
  "serija": "uvoz-kol1-2024",
  "predmetId": 3,
  "roditeljId": 41,
  "naziv": "Pretvaranje 2 → 10 i 2 → 16",
  "tezina": 2,
  "uputstvo": "Binarni broj od 6 do 8 cifara pretvoriti u dekadni i heksadekadni. Rešenje: zbir stepena dvojke, pa grupe po 4 bita. Bez razlomljenih brojeva.",
  "polja": [
    {"kljuc": "a", "oznaka": "a) dekadni", "tip": "BROJ", "odstupanje": 0},
    {"kljuc": "b", "oznaka": "b) heksadekadni", "tip": "TEKST", "zanemariVelicinu": true},
    {"kljuc": "c", "oznaka": "c) Da li je broj paran?", "tip": "JEDAN_TACAN", "opcije": [{"tekst": "da"}, {"tekst": "ne"}]},
    {"kljuc": "d", "oznaka": "d) Sa kojim brojevima je deljiv?", "tip": "VISE_TACNIH", "opcije": [{"tekst": "$2$"}, {"tekst": "$3$"}, {"tekst": "$5$"}]}
  ]
}
```

## Odgovori
`odgovori` imaju tačno jedno svojstvo po polju šeme, prema tipu: `BROJ` `broj`, `TEKST` `prihvatljivi` (1-10 tekstova do 200 znakova), `JEDAN_TACAN` `tacna` (id opcije), `VISE_TACNIH` `tacne` (id-jevi opcija). Svako polje mora imati odgovor, a ključ van šeme je greška. Primer `napravi_varijantu` za grupu iznad (opcije polja `c` su dobile id-jeve 1 i 2, polja `d` 1, 2 i 3):

```json
{
  "serija": "uvoz-kol1-2024",
  "grupaId": 57,
  "naziv": "Kol. 1, 2023/24, zad. 2",
  "tekst": "Broj $101101_{(2)}$ pretvori u dekadni i heksadekadni sistem.",
  "resenje": "\\begin{align*}\n101101_{(2)} &= 2^5 + 2^3 + 2^2 + 2^0 = 32 + 8 + 4 + 1 = 45_{(10)}\\\\\n0010\\,1101_{(2)} &= 2D_{(16)}\n\\end{align*}\nBroj 45 je neparan i deljiv sa 3 i 5.",
  "odgovori": {
    "a": {"broj": 45},
    "b": {"prihvatljivi": ["2D", "0x2D"]},
    "c": {"tacna": 2},
    "d": {"tacne": [2, 3]}
  }
}
```

## Izmena šeme: `izmeni_polja_grupe`
- Šalje se cela nova šema, i samo za tvoju grupu (izvor `CLAUDE`). Za svaku opciju koju zadržavaš vrati njen `id` (preimenovanje = isti `id`, nov `tekst`). Opcija bez `id` je **nova**, a opcija koju ne navedeš se briše zajedno sa odgovorima koji na nju pokazuju.
- Server pre upisa isproba izmenu: ako bi ijedna varijanta izgubila postojeći odgovor ili bi ijedna `AKTIVNA` pala u nacrt (npr. novo polje), odgovor je 409 `Izmena polja bi pogodila varijante: <id, ...>.` i ništa se ne upisuje. Promena tipa postojećeg polja dok grupa ima aktivnih varijanti je 409 `Tip polja 'x' se ne menja dok grupa ima aktivne varijante.` Tada napravi novu grupu ili reci Novici.

## Varijante
- `napravi_varijantu`: `grupaId` (list sa `tezina`), `tekst`, `resenje`, `odgovori`, `nivo` (obavezan, 1-3: 1 lakša, 2 uobičajena, 3 teža unutar tipa; procenu obrazloži Novici kad nije očigledna), opciono `naziv` (izvor zadatka, do 120 znakova) i `prilozi`. Server renderuje odmah i vraća `radnja: "nova"`, varijantu sa `renderStatus`, `link` i `linkPregleda`.
- Greška rendera nije neuspeh poziva: varijanta je upisana sa `renderStatus: "GRESKA"`, `renderGreska` (`linija` u tvom tekstu, `poruka`) i `renderDeo` (`tekst` ili `resenje`). Popravi je sa `izmeni_varijantu` (tvoj nacrt se menja na mestu). Novica ne može da odobri varijantu bez uspelog rendera.
- Nesiguran LaTeX prvo probaj sa `proba_rendera` (`predmetId`, `tekst`, `resenje`, `prilozi`): isti render i ista pravila, ništa se ne upisuje, vraća `{ok, greska, deo}` i sliku sa rešenjem. Najviše 3 probe istovremeno po serveru (429 `Previše proba u isto vreme.`), zato probe šalji jednu po jednu.
- `izmeni_varijantu` je delimična izmena: izostavljen ključ ostaje kakav jeste, `null` briše `naziv`, `resenje` ili `prilozi` (`tekst`, `odgovori` i `nivo` se ne brišu: `Polje 'tekst' se ne briše.`). Nad tuđom ili odobrenom varijantom pravi kopiju (`vodic pravila`). Izmena **samo** `nivo` menja tvoj neodobren nacrt na mestu; nad tuđom ili odobrenom varijantom je 409 `Nivo odobrene ili Novicine varijante menja Novica u banci.` (ne pravi kopiju): predloži Novici promenu rečima.
- `pretraga_banke` filtrira i po `nivo`, a `grupa` i `varijanta` ga vraćaju za svaku varijantu. Kad praviš varijante grupe koja ide na čas, pazi da svaki nivo koji Novica koristi ima dovoljno varijanti (na času svi studenti dobijaju isti nivo).
- `dupliraj_varijantu` pravi tvoj nacrt kao polaznu tačku za sličnu varijantu; `obrisi_varijantu` briše samo tvoj neodobren nacrt.

## Slike rendera
- `napravi_varijantu`, `izmeni_varijantu` i `proba_rendera` vraćaju jednu PNG sliku (`slika`, podrazumevano true): zadatak gore, rešenje dole, tanka crta između, duža ivica najviše 1568 px. `varijanta` vraća sliku samo uz `slika: true`.
- U grupi namene `PREDAVANJE` slika ima zadatak i rešenje. U grupi namene `TEST` slika ima samo zadatak, a rešenje tek uz `slikaResenja: true`: rešenja ispita ne treba da se gomilaju u sesijama, pa ga traži samo kad ga stvarno proveravaš. `proba_rendera` uvek prikazuje poslato rešenje.
- Pogledaj svaku sliku: da li se čita na telefonu, da li je rešenje tačno i da li se slaže sa odgovorima.
- Kad slika ne stigne na vreme (spor render, zauzet servis), alat ipak uspe i doda tekst `Slika nije stigla na vreme.`; sliku tada traži `varijanta` sa `slika: true`.
