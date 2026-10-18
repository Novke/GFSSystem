package tri.novica.gfssystem.mcp.servis;

import java.util.List;

/** Jedna strana spiska za Kloda; {@code strana} je 1-based, {@code ukupno} je broj svih stavki. */
public record McpStrana<T>(List<T> stavke, int strana, int brojStrana, long ukupno) {
}
