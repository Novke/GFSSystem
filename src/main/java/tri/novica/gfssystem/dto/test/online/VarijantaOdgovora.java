package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Varijanta u pregledu po odgovorima. */
public record VarijantaOdgovora(Long varijantaId, int stavkaRb, String naziv, String tip, int nivo, List<String> grupeTesta, String svgId, String resenjeSvgId, int primeraka, List<PoljeOdgovora> polja) {
}
