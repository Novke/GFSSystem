package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.entity.banka.Namena;

/**
 * Koren stabla banke u alatu {@code kontekst}, sa brojačima celog podstabla kao {@code GrupaCvor}: sve varijante, AKTIVNE,
 * nacrti, Klodovi nacrti (CLAUDE, NACRT) i greške rendera. Celo stablo daje {@code stablo_banke}.
 */
public record McpKorenBanke(Long grupaId, String naziv, Namena namena, int brojVarijanti, int brojAktivnih,
                            int brojNacrta, int brojClaudeNacrta, int brojGresaka) {
}
