package tri.novica.gfssystem.mcp.servis;

import org.springframework.stereotype.Service;

/**
 * Fasada: Kontekst i vodič za Kloda (spec MCP 4.2): predmeti, okruženje, preambula, tekstovi domaćih.
 * Jedino mesto gde MCP dodiruje postojeće servise; zove samo metode sa
 * {@code McpOpsegTest.DOZVOLJENE_METODE}, a skup njenih javnih metoda je zakucan u {@code McpFasadeTest.METODE}.
 */
@Service
public class McpKontekst {
}
