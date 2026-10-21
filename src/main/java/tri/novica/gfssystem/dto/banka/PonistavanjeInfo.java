package tri.novica.gfssystem.dto.banka;

import java.util.List;

/**
 * Ishod poništavanja serije (spec MCP 4.3: {@code ponisti_seriju}, i {@code POST /banka/serije/{serija}/odbaci} samo za
 * banku): šta je obrisano i šta je preskočeno, sa razlogom. {@code potvrdjeno=false}: ništa nije obrisano, a
 * {@code obrisano} je spisak onoga što bi se obrisalo.
 */
public record PonistavanjeInfo(boolean potvrdjeno, List<StavkaPonistavanja> obrisano, List<StavkaPonistavanja> preskoceno) {
}
