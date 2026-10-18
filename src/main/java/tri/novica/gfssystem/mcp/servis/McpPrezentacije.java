package tri.novica.gfssystem.mcp.servis;

import org.springframework.stereotype.Service;

/**
 * Fasada: Prezentacije za Kloda (spec MCP 4.4): pregled, slajd, pravljenje prezentacije i INFO/PITANJE
 * slajda. Jedino mesto gde MCP dodiruje postojeće servise; zove samo metode sa
 * {@code McpOpsegTest.DOZVOLJENE_METODE}, a skup njenih javnih metoda je zakucan u {@code McpFasadeTest.METODE}.
 */
@Service
public class McpPrezentacije {
}
