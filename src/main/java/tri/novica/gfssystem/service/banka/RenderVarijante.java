package tri.novica.gfssystem.service.banka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.banka.PrilogCmd;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.dto.banka.RenderPrilog;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Render varijante (spec banke 2.3, 3.2): tekst i rešenje se prevode kao dva samostalna dokumenta za telefon
 * ({@link LatexDokument#zaTelefon}), prvo tekst pa rešenje (kad nije prazno), svaki u svoj SVG. Bez transakcije i bez
 * upisa: čuvanje SVG-a i stanje varijante su posao {@code VarijantaService}.
 * <p>
 * Deny-lista tela ({@link LatexPravila#zabranjenaKomandaUTelu}) se proverava za oba dela pre bilo kakvog poziva;
 * preambulu proverava servis preambule pri čuvanju (deny-lista i {@link #proveriPreambulu probni render}), ovde ne. Greška u
 * bilo kom delu je ceo render neuspeo ({@code ok=false} sa delom i linijom relativnom na taj deo); kad padne samo rešenje,
 * ishod nosi i SVG zadatka (proba ga prikazuje), a {@code VarijantaService} ga ne čuva (varijanta zadržava poslednji uspeo par
 * zadatak i rešenje). Uz SVG (ili umesto njega) ide i PNG istog dela iz istog prevođenja kad ga traži MCP (spec MCP 6.4);
 * PNG se ne čuva. Poruka greške: red {@code dokument.tex:N:} iz loga
 * ({@link LatexGreska#izLoga}); kad ga u kraju loga nema (greška iz paketa, istek vremena, red ispao iz poslednjih
 * linija), poruka i linija koje je našao servis (linija svedena na deo); bez obe opšta poruka. Odbijen zahtev (4xx,
 * {@link RenderOdgovor#odbijen()}, poruka je telo odgovora) nikad ne prikazuje telo: nastavnik dobija opštu poruku,
 * telo ide u log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RenderVarijante {

    static final String DEO_TEKST = "tekst";
    static final String DEO_RESENJE = "resenje";
    static final String LATEX_GRESKA = "LaTeX greška, pogledaj log.";
    static final String NEDOZVOLJEN_PRILOG = "Prilog mora biti PNG, JPEG ili PDF.";
    /** Telo probnog dokumenta preambule ({@link #proveriPreambulu}): jedna kratka linija. */
    static final String PROBA_PREAMBULE = "Proba preambule.";
    /**
     * Verzija omota i načina rendera, deo heša: uvećava se kad se promeni {@code LatexDokument} omot (klasa, opcije) ili
     * nešto drugo što menja izgled istog ulaza, pa svaki stari heš zastari i {@code renderuj-sve} renderuje banku ponovo.
     */
    static final int RENDER_VERZIJA = 1;
    private static final String FORMAT = "svg";
    private static final List<String> SVG_I_PNG = List.of(FORMAT, "png");
    private static final List<String> SAMO_PNG = List.of("png");
    private static final char RAZDVAJAC = '\u0000';
    private static final Map<String, String> EKSTENZIJE =
            Map.of("image/png", "png", "image/jpeg", "jpg", "application/pdf", "pdf");
    /** Kolona {@code render_greska}: {@code <deo>:<linija ili prazno>: <poruka>}, npr. {@code resenje:12: Undefined control sequence.} */
    private static final Pattern KOLONA = Pattern.compile("(tekst|resenje):(\\d{0,9}): (.*)", Pattern.DOTALL);

    private final LatexRender latexRender;
    private final MedijService medijService;

    /**
     * Ishod rendera: uspeh nosi SVG zadatka i (kad rešenje postoji) rešenja, neuspeh grešku i deo
     * ({@value #DEO_TEKST} ili {@value #DEO_RESENJE}); neuspeh rešenja nosi i SVG zadatka koji se preveo, neuspeh teksta
     * nijedan SVG. {@code png}/{@code resenjePng} isto, samo kad je PNG tražen; SVG je tada {@code null} ako nije tražen.
     */
    public record Ishod(boolean ok, byte[] svg, byte[] resenjeSvg, RenderGreskaInfo greska, String deo, byte[] png,
                        byte[] resenjePng) {

        /** Ishod bez PNG-a. */
        public Ishod(boolean ok, byte[] svg, byte[] resenjeSvg, RenderGreskaInfo greska, String deo) {
            this(ok, svg, resenjeSvg, greska, deo, null, null);
        }
    }

    /** Greška pročitana iz kolone {@code render_greska}; {@code deo} je {@code null} za zapis bez dela. */
    public record Greska(String deo, Integer linija, String poruka) {
        public RenderGreskaInfo info() {
            return new RenderGreskaInfo(linija, poruka);
        }
    }

    private record Deo(byte[] svg, byte[] png, RenderGreskaInfo greska) {
    }

    /** Deny-lista za tekst i rešenje: 400 {@code Komanda <ime> nije dozvoljena.} za prvu zabranjenu komandu. */
    public static void proveriTelo(String tekst, String resenje) {
        Stream.of(tekst, resenje).forEach(telo -> LatexPravila.zabranjenaKomandaUTelu(telo).ifPresent(komanda -> {
            throw new SystemException("Komanda " + komanda + " nije dozvoljena.", HttpStatus.BAD_REQUEST);
        }));
    }

    /**
     * Prevodi tekst pa rešenje (prazno rešenje se preskače) sa istim prilozima, učitanim jednom; samo SVG.
     *
     * @param preambula preambula predmeta; {@code null} ili prazna = podrazumevana
     * @throws SystemException  400 za zabranjenu komandu ili prilog koji nije PNG, JPEG ili PDF (pre poziva servisa)
     * @throws RenderNedostupan kad render servis ne radi ili vrati neupotrebljiv SVG
     */
    public Ishod renderuj(String preambula, String tekst, String resenje, List<PrilogCmd> prilozi) {
        return renderuj(preambula, tekst, resenje, prilozi, true, false);
    }

    /**
     * Kao {@link #renderuj(String, String, String, List)}, sa izborom formata: svaki deo je jedno prevođenje koje vraća
     * sve tražene formate ({@code svg} za čuvanje, {@code png} za Kloda). Bez PNG-a ide stari poziv sa jednim formatom
     * ({@code latexRender.renderuj("svg", ...)}), isti zahtev kao pre PNG-a.
     *
     * @throws IllegalArgumentException kad nije tražen nijedan format
     * @throws RenderNedostupan         kad render servis ne radi ili ne vrati svaki traženi format
     */
    public Ishod renderuj(String preambula, String tekst, String resenje, List<PrilogCmd> prilozi, boolean svg,
                          boolean png) {
        if (!svg && !png) {
            throw new IllegalArgumentException("Render traži bar jedan format.");
        }
        proveriTelo(tekst, resenje);
        List<RenderPrilog> zaRender = zaRender(prilozi);
        Deo zadatak = prevedi(preambula, tekst, zaRender, svg, png);
        if (zadatak.greska() != null) {
            return new Ishod(false, null, null, zadatak.greska(), DEO_TEKST);
        }
        if (resenje == null || resenje.isBlank()) {
            return new Ishod(true, zadatak.svg(), null, null, null, zadatak.png(), null);
        }
        Deo r = prevedi(preambula, resenje, zaRender, svg, png);
        if (r.greska() != null) {
            return new Ishod(false, zadatak.svg(), null, r.greska(), DEO_RESENJE, zadatak.png(), null);
        }
        return new Ishod(true, zadatak.svg(), r.svg(), null, null, zadatak.png(), r.png());
    }

    /**
     * Ključ keša rendera: SHA-256 (hex) nad {@link #RENDER_VERZIJA}, preambulom koja se zaista prevodi
     * ({@link LatexDokument#preambulaIli}), tekstom, rešenjem ({@code null} = prazno) i prilozima sortiranim po imenu kao
     * {@code ime:medijId}, spojenim znakom {@code \u0000}.
     */
    public String hash(String preambula, String tekst, String resenje, List<PrilogCmd> prilozi) {
        return hash(RENDER_VERZIJA, preambula, tekst, resenje, prilozi);
    }

    static String hash(int verzija, String preambula, String tekst, String resenje, List<PrilogCmd> prilozi) {
        Comparator<String> nullPrvi = Comparator.nullsFirst(Comparator.naturalOrder());
        String spisak = (prilozi == null ? List.<PrilogCmd>of() : prilozi).stream()
                .sorted(Comparator.comparing(PrilogCmd::ime, nullPrvi).thenComparing(PrilogCmd::medijId, nullPrvi))
                .map(p -> p.ime() + ":" + p.medijId())
                .collect(Collectors.joining(","));
        return TokenHash.od(verzija + String.valueOf(RAZDVAJAC) + LatexDokument.preambulaIli(preambula) + RAZDVAJAC
                + prazno(tekst) + RAZDVAJAC + prazno(resenje) + RAZDVAJAC + spisak);
    }

    /**
     * Probni render preambule pre čuvanja: dokument za telefon sa tom preambulom i telom {@value #PROBA_PREAMBULE}, bez
     * priloga. Prazno kad se prevede; inače greška (poruka po istim pravilima kao za varijantu) sa linijom relativnom na
     * preambulu, ili bez linije kad greška nije u njoj (npr. tek na {@code \begin{document}}).
     *
     * @throws RenderNedostupan kad render servis ne radi
     */
    public Optional<RenderGreskaInfo> proveriPreambulu(String preambula) {
        LatexDokument.Dokument dokument = LatexDokument.zaTelefon(preambula, PROBA_PREAMBULE);
        RenderOdgovor odgovor = latexRender.renderuj(FORMAT, dokument.tex(), List.of());
        if (odgovor.ok()) {
            return Optional.empty();
        }
        RenderGreskaInfo g = greska(odgovor, LatexDokument.POMAK_PREAMBULE);
        if (g.linija() != null && g.linija() > LatexDokument.brojLinijaPreambule(preambula)) {
            g = new RenderGreskaInfo(null, g.poruka());
        }
        return Optional.of(g);
    }

    /** Ekstenzija fajla priloga u radnom direktorijumu servisa, po mime-u medija; drugo je 400. */
    static String ekstenzija(String mime) {
        String ekstenzija = mime == null ? null : EKSTENZIJE.get(mime);
        if (ekstenzija == null) {
            throw new SystemException(NEDOZVOLJEN_PRILOG, HttpStatus.BAD_REQUEST);
        }
        return ekstenzija;
    }

    /**
     * Zapis greške u kolonu {@code render_greska}; čita ga {@link #izKolone}. Bez dela (greška pre prevođenja, npr. prilog
     * bez fajla) samo poruka.
     */
    public static String uKolonu(String deo, RenderGreskaInfo greska) {
        if (deo == null) return greska.poruka();
        return deo + ":" + (greska.linija() == null ? "" : greska.linija()) + ": " + greska.poruka();
    }

    /** Greška iz kolone; {@code null} za praznu kolonu, a zapis bez dela je cela poruka bez linije. */
    public static Greska izKolone(String kolona) {
        if (kolona == null) return null;
        Matcher m = KOLONA.matcher(kolona);
        if (!m.matches()) return new Greska(null, null, kolona);
        return new Greska(m.group(1), m.group(2).isEmpty() ? null : Integer.valueOf(m.group(2)), m.group(3));
    }

    private List<RenderPrilog> zaRender(List<PrilogCmd> prilozi) {
        if (prilozi == null || prilozi.isEmpty()) return List.of();
        Base64.Encoder base64 = Base64.getEncoder();
        List<RenderPrilog> rezultat = new ArrayList<>();
        for (PrilogCmd p : prilozi) {
            MedijService.Fajl fajl = medijService.ucitaj(p.medijId());
            String ekstenzija = ekstenzija(fajl.mime());
            rezultat.add(new RenderPrilog(p.ime(), ekstenzija, base64.encodeToString(sadrzaj(fajl))));
        }
        return rezultat;
    }

    private static byte[] sadrzaj(MedijService.Fajl fajl) {
        try {
            return fajl.sadrzaj().getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Deo prevedi(String preambula, String telo, List<RenderPrilog> prilozi, boolean svg, boolean png) {
        LatexDokument.Dokument dokument = LatexDokument.zaTelefon(preambula, telo);
        RenderOdgovor odgovor = png
                ? latexRender.renderuj(svg ? SVG_I_PNG : SAMO_PNG, dokument.tex(), prilozi)
                : latexRender.renderuj(FORMAT, dokument.tex(), prilozi);
        if (odgovor.ok()) {
            return new Deo(svg ? dekodiraj(odgovor.svg(), "SVG") : null, png ? dekodiraj(odgovor.png(), "PNG") : null, null);
        }
        return new Deo(null, null, greska(odgovor, dokument.pomakLinija()));
    }

    private static byte[] dekodiraj(String base64, String format) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            log.warn("Render servis je vratio {} koji nije base64.", format);
            throw new RenderNedostupan(e);
        }
    }

    private static RenderGreskaInfo greska(RenderOdgovor odgovor, int pomakLinija) {
        RenderGreskaInfo servis = odgovor.greska();
        if (odgovor.odbijen()) {
            // poruka odbijenog zahteva (4xx) je telo odgovora servisa, nije za nastavnika
            log.warn("Render servis je odbio zahtev: {}", servis == null ? null : servis.poruka());
            return new RenderGreskaInfo(null, LatexRender.ODBIJEN_ZAHTEV);
        }
        LatexGreska.Greska izLoga = LatexGreska.izLoga(odgovor.log(), pomakLinija).orElse(null);
        if (izLoga != null) {
            return new RenderGreskaInfo(izLoga.linija(), prazan(izLoga.poruka()) ? LATEX_GRESKA : izLoga.poruka());
        }
        if (servis != null && !prazan(servis.poruka())) {
            Integer linija = servis.linija() == null ? null : Math.max(1, servis.linija() - pomakLinija);
            return new RenderGreskaInfo(linija, servis.poruka());
        }
        return new RenderGreskaInfo(null, LATEX_GRESKA);
    }

    private static boolean prazan(String s) {
        return s == null || s.isBlank();
    }

    private static String prazno(String s) {
        return s == null ? "" : s;
    }
}
