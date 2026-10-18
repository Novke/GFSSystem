package tri.novica.gfssystem.mcp.servis;

import org.springframework.stereotype.Service;

/**
 * Fasada: Banka zadataka za Kloda (spec MCP 4.3): grupe, varijante, proba rendera, isključene teme, poništavanje
 * serije. Jedino mesto gde MCP dodiruje postojeće servise; zove samo metode sa
 * {@code McpOpsegTest.DOZVOLJENE_METODE}, a skup njenih javnih metoda je zakucan u {@code McpFasadeTest.METODE}.
 */
@Service
public class McpBanka {
}
