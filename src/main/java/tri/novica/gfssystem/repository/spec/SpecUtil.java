package tri.novica.gfssystem.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tri.novica.gfssystem.utility.SkolskaGodina;

import java.time.LocalDate;
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

    // ---- zajednički filteri za entitete sa kolonom "datum" (domaći, testovi); null = bez filtera

    /** Filter po FK koloni veze ({@code asocijacija.id}), bez join-a. */
    public static <T> Specification<T> veza(String asocijacija, Long id) {
        if (id == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.equal(root.get(asocijacija).get("id"), id);
    }

    /** Školska godina: {@code datum} između 1. 10. {@code godina} i 30. 9. {@code godina+1} (uključivo). */
    public static <T> Specification<T> skolskaGodina(Integer godina) {
        if (godina == null) return Specification.unrestricted();
        LocalDate od = SkolskaGodina.pocetak(godina);
        LocalDate doDatuma = SkolskaGodina.kraj(godina);
        return (root, query, cb) -> cb.between(root.get("datum"), od, doDatuma);
    }

    public static <T> Specification<T> datumOd(LocalDate od) {
        if (od == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("datum"), od);
    }

    public static <T> Specification<T> datumDo(LocalDate doDatuma) {
        if (doDatuma == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("datum"), doDatuma);
    }

    /** {@code false} obuhvata i stare redove sa {@code null} (entiteti podrazumevaju false). */
    public static <T> Specification<T> logicko(String polje, Boolean vrednost) {
        if (vrednost == null) return Specification.unrestricted();
        if (vrednost) return (root, query, cb) -> cb.isTrue(root.get(polje));
        return (root, query, cb) -> cb.or(cb.isFalse(root.get(polje)), cb.isNull(root.get(polje)));
    }

    /** {@code lower(polje) like %q%}; q se trim-uje, prazan q je bez filtera. */
    public static <T> Specification<T> tekst(String polje, String q) {
        String obrazac = likeObrazac(q);
        if (obrazac == null) return Specification.unrestricted();
        return (root, query, cb) -> sadrzi(cb, root.get(polje), obrazac);
    }

    /** Ne dodaje fetch u count upit ({@code Long} rezultat). */
    public static boolean jeCountUpit(jakarta.persistence.criteria.CriteriaQuery<?> query) {
        return query == null || Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
    }
}
