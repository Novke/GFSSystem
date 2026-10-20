package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.entity.uzivo.VrstaMedija;

/**
 * Odgovor upload-a za Kloda ({@code POST /mcp/prilozi}, {@code POST /mcp/slike}; spec MCP 4.5): id medija za
 * {@code prilozi} varijante ili sliku u slajdu, vrsta ({@code PRILOG_SLIKA}, {@code PRILOG_PDF} ili {@code SLIKA}) i
 * {@code mime} prepoznati po prvim bajtovima, veličina u bajtovima. Bez naziva fajla: Klod ga već zna.
 */
public record McpMedij(String medijId, VrstaMedija vrsta, String mime, long velicina) {
}
