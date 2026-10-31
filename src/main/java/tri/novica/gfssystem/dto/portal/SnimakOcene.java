package tri.novica.gfssystem.dto.portal;

import java.util.List;

/**
 * Snimak predloga ocene jednog studenta u trenutku objave (spec 12.4), JSON u {@code objavljene_ocene.snimak}: predlog,
 * ukupno, predispitne (aktivnost + domaći) i poeni po tipu testa.
 */
public record SnimakOcene(Integer predlogOcene, double ukupno, double poeniPredispitne, double poeniAktivnost,
                          double poeniDomaci, List<PoeniTesta> testovi) {
}
