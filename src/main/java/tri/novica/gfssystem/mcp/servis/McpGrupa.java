package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.dto.banka.GrupaDetails;

import java.util.List;

/**
 * Rezultat alata {@code grupa} (spec MCP 4.3): grupa sa šemom polja (id-jevi opcija), uputstvom, putanjom, izvorom,
 * serijom i isključenošću, sažetak svih njenih varijanti po {@code rb} (i arhiviranih; unutrašnji čvor nema varijanti) i
 * link grupe u UI-ju.
 */
public record McpGrupa(GrupaDetails grupa, List<McpVarijantaSazetak> varijante, String link) {
}
