package tri.novica.gfssystem.mcp.servis;

/** Rezultat {@code vrati_izmenu}: {@code radnja} je {@code vracen} ili {@code obnovljen} (slajd je dobio nov id). */
public record McpVracanje(String radnja, Long slajdId, Long izmenaId, String link) {
}
