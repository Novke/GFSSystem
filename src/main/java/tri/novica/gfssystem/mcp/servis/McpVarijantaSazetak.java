package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;

/**
 * Varijanta u alatu {@code grupa}: bez teksta i bez id-jeva SVG-a (javni URL-ovi, modelu ne trebaju); {@code zamenjuje} je
 * id originala koji ova Klodova kopija zamenjuje ({@code null} za ostale). Ceo sadržaj daje {@code varijanta}.
 */
public record McpVarijantaSazetak(Long id, String naziv, StatusVarijante status, IzvorVarijante izvor,
                                  RenderStatus renderStatus, Long zamenjuje) {
}
