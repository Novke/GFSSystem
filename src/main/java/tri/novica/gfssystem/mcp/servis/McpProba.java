package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;

/**
 * Rezultat alata {@code proba_rendera} (spec MCP 4.3): {@code RenderProbaInfo} bez SVG-a. {@code ok} kad su se preveli
 * tekst i (poslato) rešenje; inače greška sa linijom relativnom na deo i deo u kome je ({@code tekst} ili
 * {@code resenje}). Slika ide uz rezultat, ne u njemu.
 */
public record McpProba(boolean ok, RenderGreskaInfo greska, String deo) {
}
