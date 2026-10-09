package tri.novica.gfssystem.repository.spec;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.utility.SkolskaGodina;

import java.time.LocalDate;

/**
 * Filteri liste predavanja. Null argument znači "bez filtera" ({@link Specification#unrestricted()}, što se
 * bezbedno spaja sa {@code Specification.allOf}); grupa je nullable, pa se filtrira po FK koloni
 * ({@code grupa.id}) bez inner join-a, a za prikaz se grupa dohvata {@code LEFT JOIN FETCH}-om ({@link #zaPrikaz()}).
 */
public final class PredavanjeSpecs {

    private PredavanjeSpecs() {
    }

    /**
     * Predmet i grupa u istom upitu (bez N+1 za {@code @ManyToOne}); grupa kroz LEFT JOIN jer može biti null.
     * Count upit stranice ({@code Long} rezultat) ne dobija fetch.
     */
    public static Specification<Predavanje> zaPrikaz() {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("predmet", JoinType.INNER);
                root.fetch("grupa", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<Predavanje> predmet(Long predmetId) {
        if (predmetId == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.equal(root.get("predmet").get("id"), predmetId);
    }

    public static Specification<Predavanje> grupa(Long grupaId) {
        if (grupaId == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.equal(root.get("grupa").get("id"), grupaId);
    }

    /** Školska godina: datum između 1. 10. {@code godina} i 30. 9. {@code godina+1} (uključivo). */
    public static Specification<Predavanje> godina(Integer godina) {
        if (godina == null) return Specification.unrestricted();
        LocalDate od = SkolskaGodina.pocetak(godina);
        LocalDate doDatuma = SkolskaGodina.kraj(godina);
        return (root, query, cb) -> cb.between(root.get("datum"), od, doDatuma);
    }

    /** {@code false} obuhvata i stare redove sa {@code zavrseno = null} (entitet podrazumeva false). */
    public static Specification<Predavanje> zavrseno(Boolean zavrseno) {
        if (zavrseno == null) return Specification.unrestricted();
        if (zavrseno) return (root, query, cb) -> cb.isTrue(root.get("zavrseno"));
        return (root, query, cb) -> cb.or(cb.isFalse(root.get("zavrseno")), cb.isNull(root.get("zavrseno")));
    }

    /** {@code lower(tema) like %q%}; q se trim-uje, prazan q je bez filtera. */
    public static Specification<Predavanje> tema(String q) {
        String obrazac = SpecUtil.likeObrazac(q);
        if (obrazac == null) return Specification.unrestricted();
        return (root, query, cb) -> SpecUtil.sadrzi(cb, root.get("tema"), obrazac);
    }

    public static Specification<Predavanje> od(LocalDate od) {
        if (od == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("datum"), od);
    }

    public static Specification<Predavanje> doDatuma(LocalDate doDatuma) {
        if (doDatuma == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("datum"), doDatuma);
    }
}
