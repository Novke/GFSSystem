package tri.novica.gfssystem.mcp.servis;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Rezultat Klodovog upisa varijante ({@code napravi_varijantu}, {@code izmeni_varijantu}; spec MCP 4.3): {@code radnja}
 * {@code nova}, {@code izmenjena} (sopstveni nacrt na mestu) ili {@code kopija} (nov nacrt koji zamenjuje original), sačuvana
 * varijanta bez id-jeva SVG-a i {@code link} na nju. {@code linkOriginala} postoji samo uz {@code kopija}, a
 * {@code linkPregleda} (pregled nacrta serije) samo iz {@code napravi_varijantu}; odsutni se ne pišu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpUpisVarijante(String radnja, McpVarijanta varijanta, String link, String linkOriginala,
                               String linkPregleda) {
}
