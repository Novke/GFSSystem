package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.uzivo.PrezentacijaInfo;
import tri.novica.gfssystem.dto.uzivo.StavkaIzbora;
import tri.novica.gfssystem.dto.uzivo.VrstaPrezentacije;

import java.util.*;

/**
 * Redosled prezentacija po vežbama i predlog sledećih (spec redosled prezentacija, čisto, bez baze). Front ima isto
 * pravilo redosleda ({@code redosled-prezentacija.ts}); menjaju se zajedno.
 */
public final class RedosledPrezentacija {

    /** Koliko prezentacija predlog nudi. */
    public static final int NAJVISE_PREDLOGA = 3;

    /**
     * Broj vežbe rastuće (bez broja na kraju), u istoj vežbi gradivo, pa pitanja, pa zadaci, zatim naziv (bez razlike
     * velikih slova) i id.
     */
    public static final Comparator<PrezentacijaInfo> REDOSLED = Comparator
            .comparing(PrezentacijaInfo::vezba, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PrezentacijaInfo::vrsta)
            .thenComparing(PrezentacijaInfo::naziv, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PrezentacijaInfo::id);

    private RedosledPrezentacija() {
    }

    /**
     * Predlog sledećih za grupu, iz stavki već poređanih po {@link #REDOSLED}. Kandidat ima broj vežbe, nije u toku
     * (drugo izvođenje bi bilo 409) i grupa ga nije puštala. Prvo dug: zadaci vežbi pre najdalje puštene (gradivo se
     * često pokaže van aplikacije, zadaci su ono što grupa duguje). Zatim sve od najdalje puštene vežbe nadalje po redu
     * (njeno nepušteno gradivo, pa njeni zadaci, pa sledeća vežba). Starije nepušteno gradivo se ne nudi. Bez ijednog
     * puštanja sa brojem: sve po redu od početka.
     */
    public static List<Long> zaGrupu(List<StavkaIzbora> uRedu) {
        int najdalja = uRedu.stream()
                .filter(s -> s.pustenoGrupi() != null && s.prezentacija().vezba() != null)
                .mapToInt(s -> s.prezentacija().vezba())
                .max().orElse(-1);
        List<PrezentacijaInfo> kandidati = uRedu.stream()
                .filter(s -> s.pustenoGrupi() == null)
                .map(StavkaIzbora::prezentacija)
                .filter(RedosledPrezentacija::moze)
                .toList();
        LinkedHashSet<Long> predlog = new LinkedHashSet<>();
        kandidati.stream()
                .filter(p -> p.vrsta() == VrstaPrezentacije.ZADACI && p.vezba() < najdalja)
                .forEach(p -> predlog.add(p.id()));
        kandidati.stream()
                .filter(p -> p.vezba() >= najdalja)
                .forEach(p -> predlog.add(p.id()));
        return predlog.stream().limit(NAJVISE_PREDLOGA).toList();
    }

    /**
     * Predlog bez grupe (izvođenje van predavanja): prve prezentacije sa brojem vežbe posle {@code posle} po redu;
     * {@code posle} bez broja ili nepoznat daje prazan predlog.
     */
    public static List<Long> posle(List<StavkaIzbora> uRedu, Long posle) {
        List<PrezentacijaInfo> red = uRedu.stream().map(StavkaIzbora::prezentacija).toList();
        int i = -1;
        for (int k = 0; k < red.size(); k++) {
            if (red.get(k).id().equals(posle)) {
                i = k;
                break;
            }
        }
        if (i < 0 || red.get(i).vezba() == null) {
            return List.of();
        }
        return red.subList(i + 1, red.size()).stream()
                .filter(RedosledPrezentacija::moze)
                .map(PrezentacijaInfo::id)
                .limit(NAJVISE_PREDLOGA)
                .toList();
    }

    private static boolean moze(PrezentacijaInfo p) {
        return p.vezba() != null && p.aktivnoIzvodjenjeId() == null;
    }
}
