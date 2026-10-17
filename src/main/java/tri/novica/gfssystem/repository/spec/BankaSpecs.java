package tri.novica.gfssystem.repository.spec;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.entity.banka.Varijanta;

/**
 * Filteri pretrage varijanti banke. Null argument znači "bez filtera" ({@link Specification#unrestricted()}).
 * <p>
 * {@link #zaPrikaz()} ide <b>prvi</b> u {@code Specification.allOf}: učitava grupu varijante i lanac njenih predaka u
 * istom upitu (putanja grupe bez upita po nivou), a filteri po grupi koriste taj isti join. Count upit stranice ne dobija
 * fetch, pa filteri tada prave običan join.
 */
public final class BankaSpecs {

    /** Dubina stabla je najviše 6 ({@code GrupaZadatakaService.MAX_DUBINA}): grupa ima najviše 5 predaka. */
    private static final int NAJVISE_PREDAKA = 5;

    private BankaSpecs() {
    }

    /**
     * Grupa sa lancem predaka i oba render SVG medija (EAGER veze, inače po jedan upit po redu): jedan upit za stranicu,
     * bez obzira na broj varijanti i dubinu stabla. Sve su veze "prema jednom", pa straničenje ostaje u bazi.
     */
    public static Specification<Varijanta> zaPrikaz() {
        return (root, query, cb) -> {
            if (!SpecUtil.jeCountUpit(query)) {
                Fetch<?, ?> predak = root.fetch("grupa", JoinType.INNER);
                for (int i = 0; i < NAJVISE_PREDAKA; i++) {
                    predak = predak.fetch("roditelj", JoinType.LEFT);
                }
                root.fetch("renderSvg", JoinType.LEFT);
                root.fetch("renderResenjeSvg", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<Varijanta> predmet(Long predmetId) {
        if (predmetId == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.equal(grupa(root).get("predmet").get("id"), predmetId);
    }

    public static Specification<Varijanta> namena(Namena namena) {
        if (namena == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.equal(grupa(root).get("namena"), namena);
    }

    public static Specification<Varijanta> status(StatusVarijante status) {
        if (status == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** {@code lower(naziv) / lower(tekst) / lower(naziv grupe) like %q%}; q se trim-uje, prazan q je bez filtera. */
    public static Specification<Varijanta> upit(String q) {
        String obrazac = SpecUtil.likeObrazac(q);
        if (obrazac == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.or(
                SpecUtil.sadrzi(cb, root.get("naziv"), obrazac),
                SpecUtil.sadrzi(cb, root.get("tekst"), obrazac),
                SpecUtil.sadrzi(cb, grupa(root).get("naziv"), obrazac));
    }

    /**
     * Join grupe koji je {@link #zaPrikaz()} već napravio (fetch je join, ali se ne vidi u {@code getJoins()}), inače
     * novi: tako upit ima jedan join grupe, a ne dva.
     */
    @SuppressWarnings("unchecked")
    private static Join<Varijanta, GrupaZadataka> grupa(Root<Varijanta> root) {
        for (Fetch<Varijanta, ?> fetch : root.getFetches()) {
            if ("grupa".equals(fetch.getAttribute().getName()) && fetch instanceof Join<?, ?> join) {
                return (Join<Varijanta, GrupaZadataka>) join;
            }
        }
        for (Join<Varijanta, ?> join : root.getJoins()) {
            if ("grupa".equals(join.getAttribute().getName())) {
                return (Join<Varijanta, GrupaZadataka>) join;
            }
        }
        return root.join("grupa", JoinType.INNER);
    }
}
