package tri.novica.gfssystem.repository.spec;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import tri.novica.gfssystem.entity.Domaci;

import java.time.LocalDate;

/**
 * Filteri liste domaćih (isti obrazac kao {@link PredavanjeSpecs}). Grupa i predavanje su nullable, pa se za prikaz
 * dohvataju {@code LEFT JOIN FETCH}-om, a filter po grupi ide po FK koloni bez join-a.
 */
public final class DomaciSpecs {

    private DomaciSpecs() {
    }

    /** Predmet (inner), grupa i predavanje (left) u istom upitu; count upit stranice ne dobija fetch. */
    public static Specification<Domaci> zaPrikaz() {
        return (root, query, cb) -> {
            if (!SpecUtil.jeCountUpit(query)) {
                root.fetch("predmet", JoinType.INNER);
                root.fetch("grupa", JoinType.LEFT);
                root.fetch("predavanje", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<Domaci> predmet(Long predmetId) {
        return SpecUtil.veza("predmet", predmetId);
    }

    public static Specification<Domaci> grupa(Long grupaId) {
        return SpecUtil.veza("grupa", grupaId);
    }

    public static Specification<Domaci> godina(Integer godina) {
        return SpecUtil.skolskaGodina(godina);
    }

    /** {@code false} obuhvata i stare redove sa {@code pregledan = null}. */
    public static Specification<Domaci> pregledan(Boolean pregledan) {
        return SpecUtil.logicko("pregledan", pregledan);
    }

    /** Pretraga po naslovu. */
    public static Specification<Domaci> naslov(String q) {
        return SpecUtil.tekst("naslov", q);
    }

    public static Specification<Domaci> od(LocalDate od) {
        return SpecUtil.datumOd(od);
    }

    public static Specification<Domaci> doDatuma(LocalDate doDatuma) {
        return SpecUtil.datumDo(doDatuma);
    }
}
