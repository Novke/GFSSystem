package tri.novica.gfssystem.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.Locale;

/** Zajednički delovi {@code *Specs} klasa za liste (pretraga teksta). */
public final class SpecUtil {

    static final char ESCAPE = '\\';

    private SpecUtil() {
    }

    /**
     * {@code q} posle trim-a i u malim slovima, sa escape-ovanim {@code %} i {@code _}, kao {@code %q%};
     * null ako je {@code q} null ili prazan (bez filtera).
     */
    public static String likeObrazac(String q) {
        if (q == null || q.isBlank()) return null;
        String bezDzokera = q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + bezDzokera + "%";
    }

    /** {@code lower(polje) like %q%} sa doslovnim {@code %} i {@code _} iz upita. */
    public static Predicate sadrzi(CriteriaBuilder cb, Expression<String> polje, String obrazac) {
        return cb.like(cb.lower(polje), obrazac, ESCAPE);
    }
}
