package tri.novica.gfssystem.dto.portal;

import java.util.List;

/** Prisustvo na predmetu (spec 5.2): "Prisutan na {@code prisutan} od {@code ukupno}", predavanja od najnovijeg. */
public record PrisustvoStudenta(int prisutan, int ukupno, List<PredavanjeStudenta> predavanja) {
}
