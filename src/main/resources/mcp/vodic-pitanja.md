# Slajdovi: INFO i PITANJE (GFS MCP)

`dodaj_slajd` (`serija`, `prezentacijaId`, `posle?`, `slajd`) dodaje jedan slajd: bez `posle` na kraj, sa `posle` (id slajda) odmah iza tog slajda. U ovoj verziji dodaješ samo `INFO` i `PITANJE` (ostalo je 400 `Klod u ovoj verziji dodaje samo INFO i PITANJE slajdove.`), a postojeće slajdove ne menjaš. `slajd` se čita strogo: nepoznat ključ je `Nepoznato polje 'slajd.x'.`, a `null` za `postepeno` ili `tacna` je `Neispravan format podataka.` (izostavi ih ili pošalji true/false). Najviše 200 slajdova po prezentaciji; prezentacija koja se upravo izvodi se ne menja.

Zajedničko za oba tipa:
- `beleske` (do 20 000 znakova): beleške za Novicu na konzoli; studenti ih ne vide.
- `prelaz` (opciono): `{"efekat": "FADE" | "GURANJE" | "BRISANJE", "smer": "LEVO" | "DESNO" | "GORE" | "DOLE", "trajanjeMs": 100-5000}`; FADE nema smer, bez smera je DESNO, bez trajanja 500 ms.
- Slika: fajl otpremi iz Bash-a sa `gfs-mcp-upload <gfs|gfs-staging> <fajl> --slika --serija <serija>` (PNG, JPEG, GIF ili WebP, do 10 MB, vrsta `SLIKA`), pa njen `medijId` stavi u `slikaId` slajda (INFO) ili pitanja (PITANJE). Prilog zadatka iz banke (`PRILOG_*`) nije slika za slajd.

## INFO
- `naslov` (do 300 znakova), `sadrzaj` (Markdown, do 20 000), `slikaId`; potrebno je bar jedno od ta tri.
- `postepeno: true`: stavke liste se pojavljuju jedna po jedna, na klik. Stavka je red koji, bez uvlačenja, počinje sa `- `, `* `, `+ `, `1. ` ili `1) `; svaka stavka je jedan korak.
- Slika u Markdown-u sme biti samo medij vrste `SLIKA` otpremljen u GFS, i to tačno u ovom obliku, u jednom redu i bez naslova pod navodnicima: `![opis](api/public/mediji/<medijId>)`. Spoljni URL, putanja sa `/` na početku, referentni oblik (`![a][r]` sa `[r]: url`) i slika u bloku koda su 400 `Slika u tekstu mora biti otpremljena u GFS.`; u tekstu sa slikom ne piši ni `[nešto]:`. Običan link `[tekst](https://...)` je dozvoljen.
- INFO ne čita formule: `$` je tamo običan znak. Formulu na INFO slajdu napiši rečima, stavi je na sliku ili prebaci na PITANJE.

## PITANJE
`pitanje`: `tip`, `tekst` (obavezan, do 2 000 znakova, Markdown sa formulama), `slikaId?`, `vremeSekunde?` (5-600; bez njega nema odbrojavanja i pitanje zatvara Novica) i polja po tipu:

| Tip | Polja i pravila |
|---|---|
| `JEDAN_TACAN` | `opcije` 2-6 (`tekst` 1-300 znakova, `tacna`), tačno jedna tačna |
| `VISE_TACNIH` | `opcije` 2-6, bar jedna tačna |
| `ANKETA` | `opcije` 2-6, nema tačnih (`tacna` se zanemaruje) |
| `TACNO_NETACNO` | tačno dve `opcije`: prva je "Tačno", druga "Netačno" (tekst postavlja server), tačno jedna tačna |
| `KRATAK_TEKST` | `prihvatljiviOdgovori` do 20, svaki do 100 znakova (poređenje bez velikih slova, dijakritika i razmaka, ćirilica kao latinica); `tekstPrikaz` `OBLAK` (podrazumevano) ili `LISTA`; bez prihvatljivih nema tačnog odgovora |
| `BROJ` | `brojTacno`, `brojOdstupanje` >= 0 (podrazumevano 0), `odstupanjeTip` `APSOLUTNO` ili `PROCENAT`, `jedinica` do 30 znakova; bez `brojTacno` nema tačnog odgovora; student sme zarez ili tačku |
| `SKALA` | student bira 1-5; `skalaMinOznaka` i `skalaMaxOznaka` do 60 znakova; nema tačnog |

Polja koja ne važe za tip server briše.

## Formule `$…$`
- Važe u tekstu pitanja i u tekstu opcija (ne u INFO, beleškama, prihvatljivim odgovorima, jedinici ni oznakama skale) i crtaju se na projektoru i na telefonu. Unutra je LaTeX matematika: `\frac{a}{b}`, `x^2`, `\sqrt{2}`, `1011_{(2)}`.
- Pravila: posle `$` koji otvara ne sme razmak; unutra nema novog reda ni drugog `$`; pre `$` koji zatvara nema razmaka ni `\`; odmah posle `$` koji zatvara ne sme cifra. `$` odmah posle cifre ne otvara formulu (`5$` je iznos, pa ni `2$x$` nije formula; piši `2 $x$`). `\$` je uvek znak dolara, a `$$...$$` ne postoji.
- Najviše 50 formula u jednom tekstu; ostale ostaju kao izvorni tekst.

## Primeri `slajd`
Svaki primer je vrednost argumenta `slajd` u `dodaj_slajd`. `medijId` u slici je primer; pravi daje `gfs-mcp-upload --slika`.

```json
{
  "tip": "INFO",
  "naslov": "Pozicioni brojni sistemi",
  "sadrzaj": "Vrednost cifre zavisi od njenog mesta:\n\n- osnova 2: cifre 0 i 1\n- osnova 8: cifre 0 do 7\n- osnova 16: cifre 0 do 9 i A do F\n\n![Tablica stepena dvojke](api/public/mediji/0b6f2c1e-5a7d-4e3b-9c1a-2f8d4e6a7b90)",
  "beleske": "Pitati zašto je heksadekadni zgodan za bajtove.",
  "postepeno": true
}
```

```json
{
  "tip": "PITANJE",
  "pitanje": {
    "tip": "JEDAN_TACAN",
    "tekst": "Koliko je $1011_{(2)}$ u dekadnom sistemu?",
    "vremeSekunde": 30,
    "opcije": [{"tekst": "9", "tacna": false}, {"tekst": "11", "tacna": true}, {"tekst": "13", "tacna": false}]
  }
}
```

```json
{
  "tip": "PITANJE",
  "pitanje": {
    "tip": "VISE_TACNIH",
    "tekst": "Koji zapisi su ispravni heksadekadni brojevi?",
    "opcije": [{"tekst": "1F", "tacna": true}, {"tekst": "G2", "tacna": false}, {"tekst": "A0", "tacna": true}]
  }
}
```

```json
{
  "tip": "PITANJE",
  "pitanje": {
    "tip": "ANKETA",
    "tekst": "Koji sistem vam je najteži?",
    "opcije": [{"tekst": "binarni"}, {"tekst": "oktalni"}, {"tekst": "heksadekadni"}]
  }
}
```

```json
{
  "tip": "PITANJE",
  "pitanje": {
    "tip": "TACNO_NETACNO",
    "tekst": "Svaki paran binarni broj se završava cifrom 0.",
    "opcije": [{"tekst": "Tačno", "tacna": true}, {"tekst": "Netačno", "tacna": false}]
  }
}
```

```json
{
  "tip": "PITANJE",
  "pitanje": {
    "tip": "KRATAK_TEKST",
    "tekst": "Kako se zove brojni sistem sa osnovom 16?",
    "prihvatljiviOdgovori": ["heksadekadni", "heksadecimalni"],
    "tekstPrikaz": "LISTA"
  }
}
```

```json
{
  "tip": "PITANJE",
  "pitanje": {
    "tip": "BROJ",
    "tekst": "Koliko je najmanje bitova potrebno za broj $255$?",
    "vremeSekunde": 45,
    "brojTacno": 8,
    "brojOdstupanje": 0,
    "odstupanjeTip": "APSOLUTNO"
  }
}
```

```json
{
  "tip": "PITANJE",
  "pitanje": {
    "tip": "SKALA",
    "tekst": "Koliko vam je jasno pretvaranje 10 → 2?",
    "skalaMinOznaka": "nimalo",
    "skalaMaxOznaka": "potpuno"
  }
}
```
