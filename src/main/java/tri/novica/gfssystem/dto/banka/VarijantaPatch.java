package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.utility.Promena;

import java.util.List;
import java.util.Map;

/**
 * Klodova delimična izmena varijante (merge patch, spec MCP 4.1): {@link Promena#bez()} ostavlja polje kakvo jeste,
 * {@code Promena.na(null)} ga briše. Brišu se samo {@code naziv}, {@code resenje} i {@code prilozi} (pravilo nosi
 * {@code McpPatch} u alatu); servis spaja patch nad trenutnim stanjem i proverava rezultat kao {@link VarijantaCmd}.
 * Polje {@code null} (ne {@code Promena}) znači "nije poslato". {@code nivo} nije sadržaj: ne briše se, ne renderuje se,
 * a patch koji menja samo njega ({@link #samoNivo()}) servis primenjuje bez kopije (samo na sopstvenom nacrtu).
 */
public record VarijantaPatch(Promena<String> naziv, Promena<String> tekst, Promena<String> resenje,
                             Promena<Map<String, OdgovorPolja>> odgovori, Promena<List<PrilogCmd>> prilozi,
                             Promena<Integer> nivo) {

    public VarijantaPatch {
        naziv = naziv == null ? Promena.bez() : naziv;
        tekst = tekst == null ? Promena.bez() : tekst;
        resenje = resenje == null ? Promena.bez() : resenje;
        odgovori = odgovori == null ? Promena.bez() : odgovori;
        prilozi = prilozi == null ? Promena.bez() : prilozi;
        nivo = nivo == null ? Promena.bez() : nivo;
    }

    /** Bez promene nivoa. */
    public VarijantaPatch(Promena<String> naziv, Promena<String> tekst, Promena<String> resenje,
                          Promena<Map<String, OdgovorPolja>> odgovori, Promena<List<PrilogCmd>> prilozi) {
        this(naziv, tekst, resenje, odgovori, prilozi, null);
    }

    /** Postavljen je samo nivo (nijedno polje sadržaja). */
    public boolean samoNivo() {
        return nivo.postavljeno() && !naziv.postavljeno() && !tekst.postavljeno() && !resenje.postavljeno()
                && !odgovori.postavljeno() && !prilozi.postavljeno();
    }
}
