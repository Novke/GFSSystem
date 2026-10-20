package tri.novica.gfssystem.mcp.proba;

import tri.novica.gfssystem.dto.banka.VarijantaDetails;
import tri.novica.gfssystem.service.banka.VarijantaService;

/**
 * Samo za negativnu proveru u {@code McpOpsegTest}: fasada koja zove zabranjenu metodu ({@code VarijantaService.odobri}).
 * Nije bean i glavno pravilo je ne uvozi (test klase su van uvoza); test je uvozi izričito.
 */
public class LosaFasada {

    private final VarijantaService varijante;

    public LosaFasada(VarijantaService varijante) {
        this.varijante = varijante;
    }

    public VarijantaDetails odobri(Long id) {
        return varijante.odobri(id);
    }
}
