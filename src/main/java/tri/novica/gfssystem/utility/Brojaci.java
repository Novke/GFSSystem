package tri.novica.gfssystem.utility;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Brojači za listu jednim agregatnim upitom po stranici (bez N+1): upit vraća redove {@code [id, count]}
 * ({@code ... where x.id in :ids group by x.id}), ovde postaju mapa id -> broj. Prazan skup id-jeva ne ide u bazu.
 */
public final class Brojaci {

    private Brojaci() {
    }

    public static Map<Long, Long> poId(Collection<Long> ids, Function<Collection<Long>, List<Object[]>> upit) {
        if (ids.isEmpty()) return Map.of();
        return mapa(upit.apply(ids));
    }

    /** Redovi {@code [id, count]} agregatnog upita kao mapa id -> broj. */
    public static Map<Long, Long> mapa(List<Object[]> redovi) {
        Map<Long, Long> rezultat = new HashMap<>();
        for (Object[] red : redovi) {
            rezultat.put(((Number) red[0]).longValue(), ((Number) red[1]).longValue());
        }
        return rezultat;
    }
}
