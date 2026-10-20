package tri.novica.gfssystem.mcp.servis;

/**
 * Podaci za JSON odgovor plus opciona PNG slika ({@code png == null}: bez slike) i napomena (npr.
 * {@code Slika nije stigla na vreme.}); alat ih predaje {@code McpOdgovor.jsonSaSlikom}.
 */
public record McpSaSlikom<T>(T podaci, byte[] png, String napomena) {
}
