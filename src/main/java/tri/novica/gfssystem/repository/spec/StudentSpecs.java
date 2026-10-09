package tri.novica.gfssystem.repository.spec;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.utility.IndeksUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filteri liste studenata (isti obrazac kao {@link PredavanjeSpecs}). Grupa je nullable. */
public final class StudentSpecs {

    private StudentSpecs() {
    }

    /** Grupa kroz LEFT JOIN FETCH; count upit stranice ne dobija fetch. */
    public static Specification<Student> zaPrikaz() {
        return (root, query, cb) -> {
            if (!SpecUtil.jeCountUpit(query)) {
                root.fetch("grupa", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<Student> grupa(Long grupaId) {
        return SpecUtil.veza("grupa", grupaId);
    }

    /**
     * Studenti čija grupa ima {@code godinaUpisa} manju od {@code godinaUpisaReference}; studenti bez grupe ne ulaze.
     * Referentna grupa bez godine upisa nema šta da se poredi, pa rezultat je prazan.
     */
    public static Specification<Student> grupaStarijaOd(Integer godinaUpisaReference) {
        return (root, query, cb) -> godinaUpisaReference == null
                ? cb.disjunction()
                : cb.lessThan(root.get("grupa").<Integer>get("godinaUpisa"), godinaUpisaReference);
    }

    /**
     * {@code q} traži (bez obzira na velika i mala slova) u imenu, prezimenu, punom imenu {@code "ime prezime"} i u
     * indeksu bez razmaka: "gd 12" nalazi i "GD12" i stari red "GD 12" (kolona se poredi bez razmaka, a {@code q}
     * se normalizuje kao indeks). {@code %}, {@code _} i {@code \} su doslovni.
     */
    public static Specification<Student> q(String q) {
        String obrazac = SpecUtil.likeObrazac(q);
        if (obrazac == null) return Specification.unrestricted();
        String normalizovan = IndeksUtil.normalizuj(q.trim());
        String obrazacIndeksa = SpecUtil.likeObrazac(normalizovan == null ? null : normalizovan.toLowerCase(Locale.ROOT));
        return (root, query, cb) -> {
            List<Predicate> uslovi = new ArrayList<>();
            uslovi.add(SpecUtil.sadrzi(cb, root.get("ime"), obrazac));
            uslovi.add(SpecUtil.sadrzi(cb, root.get("prezime"), obrazac));
            uslovi.add(SpecUtil.sadrzi(cb, cb.concat(cb.concat(cb.coalesce(root.<String>get("ime"), ""), " "),
                    cb.coalesce(root.<String>get("prezime"), "")), obrazac));
            if (obrazacIndeksa != null) {
                uslovi.add(SpecUtil.sadrzi(cb, cb.function("replace", String.class, root.<String>get("indeks"),
                        cb.literal(" "), cb.literal("")), obrazacIndeksa));
            }
            return cb.or(uslovi.toArray(new Predicate[0]));
        };
    }
}
