package tri.novica.gfssystem.mcp.servis;

import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.OpcijaCmd;
import tri.novica.gfssystem.dto.uzivo.OpcijaDetails;
import tri.novica.gfssystem.dto.uzivo.PitanjeDetails;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaDetails;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpOdgovor;
import tri.novica.gfssystem.service.uzivo.Markdown;

import java.util.ArrayList;
import java.util.List;

/**
 * Sažet pregled prezentacije za Kloda (spec MCP 4.4), čiste funkcije bez baze. Po slajdu: id, rb, tip, beleške (prvih
 * {@value #MAX_BELESKE} znakova), prelaz, broj koraka, pa INFO naslov i sadržaj (do {@value #MAX_SADRZAJ} znakova), PITANJE
 * tip, tekst i opcije sa tačnim, PLATNO slojevi ({@link PlatnoPregled#slojevi}); skraćen tekst se završava sa
 * {@value PlatnoPregled#SKRACENO}, ceo slajd daje alat {@code slajd}.
 * <p>
 * Strane su pohlepne ({@link #strane}): redom slajdova, najviše {@value #MAX_SLAJDOVA_PO_STRANI} po strani i najviše
 * {@link #MAX_ZNAKOVA_SLAJDOVA} znakova JSON-a sažetaka (ostatak do {@link McpOdgovor#MAX_ZNAKOVA} je za polja
 * prezentacije), pa isti ulaz uvek daje istu podelu i svaka strana staje u jedan odgovor alata. Slajd veći od granice sam
 * čini stranu. Šest stvarnih vežbi od 10 do 24 slajda staje u jednu stranu ({@code PrezentacijaPregledTest}).
 */
final class PrezentacijaPregled {

    static final int MAX_SLAJDOVA_PO_STRANI = 30;
    static final int MAX_BELESKE = 200;
    static final int MAX_SADRZAJ = 1000;
    /** Granica JSON-a sažetaka slajdova jedne strane, u znakovima. */
    static final int MAX_ZNAKOVA_SLAJDOVA = McpOdgovor.MAX_ZNAKOVA - 2000;

    /** Samo za merenje dužine sažetaka (record-i sa {@code NON_NULL}, bez datuma: isti tekst kao aplikacioni mapper). */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PrezentacijaPregled() {
    }

    /**
     * Strana {@code strana} (od 1) sažetog pregleda; prezentacija bez slajdova ima jednu praznu stranu. Strana van
     * {@code 1..brojStrana} je 400 {@code Neispravan parametar: strana.}
     */
    static McpPrezentacijaPregled pregled(PrezentacijaDetails p, int strana, String link) {
        List<SlajdDetails> slajdovi = p.slajdovi() == null ? List.of() : p.slajdovi();
        List<List<McpSlajdSazetak>> strane = strane(slajdovi.stream().map(PrezentacijaPregled::sazetak).toList());
        if (strana < 1 || strana > strane.size()) {
            throw new SystemException(McpStrana.NEISPRAVNA_STRANA, HttpStatus.BAD_REQUEST);
        }
        return new McpPrezentacijaPregled(p.id(), p.naziv(), p.opis(), p.tema(), p.aktivnoIzvodjenjeId(),
                slajdovi.size(), strana, strane.size(), strane.get(strana - 1), link);
    }

    /** Sažetak jednog slajda (i za rezultat {@code dodaj_slajd}). */
    static McpSlajdSazetak sazetak(SlajdDetails s) {
        String beleske = PlatnoPregled.skrati(s.beleske(), MAX_BELESKE);
        return switch (s.tip()) {
            case INFO -> new McpSlajdSazetak(s.id(), s.rb(), s.tip(), beleske, s.prelaz(),
                    s.postepeno() ? Markdown.brojStavki(s.sadrzaj()) : 0, s.naslov(),
                    PlatnoPregled.skrati(s.sadrzaj(), MAX_SADRZAJ), null, null, null);
            case PITANJE -> new McpSlajdSazetak(s.id(), s.rb(), s.tip(), beleske, s.prelaz(), 0, null, null,
                    pitanje(s.pitanje()), null, null);
            case PLATNO -> new McpSlajdSazetak(s.id(), s.rb(), s.tip(), beleske, s.prelaz(),
                    PlatnoPregled.brojKoraka(s.platno()), null, null, null, PlatnoPregled.slojevi(s.platno()), null);
            case ZADATAK -> new McpSlajdSazetak(s.id(), s.rb(), s.tip(), beleske, s.prelaz(), 0, null, null, null, null,
                    McpZadatakSazetak.od(s.zadatak()));
        };
    }

    /**
     * Slajd kao u editoru, ali PLATNO bez poteza crteža (alat {@code slajd}); slajd bez platna se vraća isti. Prepisuje
     * sve komponente {@link SlajdDetails}: nova komponenta (npr. {@code verzija}) mora ući i ovde
     * ({@code PrezentacijaPregledTest} broji komponente).
     */
    static SlajdDetails bezPoteza(SlajdDetails s) {
        if (s.platno() == null) {
            return s;
        }
        return new SlajdDetails(s.id(), s.rb(), s.tip(), s.naslov(), s.sadrzaj(), s.slika(), s.beleske(), s.postepeno(),
                s.pitanje(), PlatnoPregled.bezPoteza(s.platno()), s.prelaz(), s.zadatak());
    }

    /**
     * Pohlepna podela: slajd ide na tekuću stranu dok ona ima manje od {@value #MAX_SLAJDOVA_PO_STRANI} slajdova i dok
     * JSON njenih sažetaka (sa zarezima) ostaje do {@link #MAX_ZNAKOVA_SLAJDOVA}; inače otvara novu. Bar jedna strana.
     */
    static List<List<McpSlajdSazetak>> strane(List<McpSlajdSazetak> slajdovi) {
        List<List<McpSlajdSazetak>> strane = new ArrayList<>();
        List<McpSlajdSazetak> tekuca = new ArrayList<>();
        int znakova = 0;
        for (McpSlajdSazetak s : slajdovi) {
            int duzina = JSON.writeValueAsString(s).length();
            if (!tekuca.isEmpty() && (tekuca.size() >= MAX_SLAJDOVA_PO_STRANI
                    || znakova + 1 + duzina > MAX_ZNAKOVA_SLAJDOVA)) {
                strane.add(List.copyOf(tekuca));
                tekuca = new ArrayList<>();
                znakova = 0;
            }
            znakova += (tekuca.isEmpty() ? 0 : 1) + duzina;
            tekuca.add(s);
        }
        if (!tekuca.isEmpty() || strane.isEmpty()) {
            strane.add(List.copyOf(tekuca));
        }
        return List.copyOf(strane);
    }

    private static McpPitanjeSazetak pitanje(PitanjeDetails p) {
        if (p == null) {
            return null;
        }
        List<OpcijaCmd> opcije = p.opcije() == null || p.opcije().isEmpty() ? null
                : p.opcije().stream().map(PrezentacijaPregled::opcija).toList();
        return new McpPitanjeSazetak(p.tip(), p.tekst(), opcije, p.brojTacno(), p.prihvatljiviOdgovori());
    }

    private static OpcijaCmd opcija(OpcijaDetails o) {
        return new OpcijaCmd(o.tekst(), o.tacna());
    }
}
