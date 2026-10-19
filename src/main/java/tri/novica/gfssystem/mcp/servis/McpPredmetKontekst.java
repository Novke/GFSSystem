package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.dto.banka.IskljucenaTemaInfo;

import java.util.List;

/**
 * Predmet u alatu {@code kontekst}: koreni banke obe namene (PREDAVANJE pa TEST, po {@code rb}), broj prezentacija,
 * isključene teme (najnovije prve) i da li predmet ima sopstvenu LaTeX preambulu ({@code false} = podrazumevana).
 */
public record McpPredmetKontekst(Long id, String naziv, List<McpKorenBanke> koreni, long brojPrezentacija,
                                 List<IskljucenaTemaInfo> iskljuceneTeme, boolean imaPreambulu) {
}
