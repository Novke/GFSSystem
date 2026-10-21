package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;

import java.time.LocalDateTime;

/**
 * Varijanta u listi grupe, po {@code rb}. {@code svgId} i {@code resenjeSvgId} su id-jevi medija poslednjeg uspelog
 * rendera ({@code api/public/mediji/{id}}); posle neuspelog rendera ostaju stari dok novi ne uspe. {@code ikadOdobrena}:
 * bila je bar jednom odobrena (vraćanje u nacrt to ne briše); {@code zamenjuje} je id originala koji ova kopija
 * zamenjuje (odobrenje kopije ga arhivira), {@code serija} Klodova serija upisa; oba {@code null} za ručno unete.
 */
public record VarijantaInfo(Long id, Long grupaId, int rb, String naziv, StatusVarijante status, IzvorVarijante izvor,
                            RenderStatus renderStatus, String svgId, String resenjeSvgId, LocalDateTime izmenjeno,
                            boolean ikadOdobrena, Long zamenjuje, String serija) {
}
