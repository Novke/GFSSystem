package tri.novica.gfssystem.repository.spec;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import tri.novica.gfssystem.entity.Test;

import java.time.LocalDate;

/** Filteri liste testova (isti obrazac kao {@link PredavanjeSpecs}). {@code Test.grupe} (CSV kolona) se ne filtrira. */
public final class TestSpecs {

    private TestSpecs() {
    }

    /** Predmet, grupa i tip testa u istom upitu (sve tri veze su obavezne); count upit stranice ne dobija fetch. */
    public static Specification<Test> zaPrikaz() {
        return (root, query, cb) -> {
            if (!SpecUtil.jeCountUpit(query)) {
                root.fetch("predmet", JoinType.INNER);
                root.fetch("grupa", JoinType.INNER);
                root.fetch("tipTesta", JoinType.INNER);
            }
            return null;
        };
    }

    public static Specification<Test> predmet(Long predmetId) {
        return SpecUtil.veza("predmet", predmetId);
    }

    public static Specification<Test> grupa(Long grupaId) {
        return SpecUtil.veza("grupa", grupaId);
    }

    public static Specification<Test> tipTesta(Long tipTestaId) {
        return SpecUtil.veza("tipTesta", tipTestaId);
    }

    public static Specification<Test> godina(Integer godina) {
        return SpecUtil.skolskaGodina(godina);
    }

    /** {@code false} obuhvata i stare redove sa {@code pregledan = null}. */
    public static Specification<Test> pregledan(Boolean pregledan) {
        return SpecUtil.logicko("pregledan", pregledan);
    }

    public static Specification<Test> od(LocalDate od) {
        return SpecUtil.datumOd(od);
    }

    public static Specification<Test> doDatuma(LocalDate doDatuma) {
        return SpecUtil.datumDo(doDatuma);
    }
}
