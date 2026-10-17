package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;

import java.time.LocalDateTime;

/**
 * Varijanta u listi grupe, po {@code rb}. {@code svgId} i {@code resenjeSvgId} su id-jevi medija poslednjeg uspelog
 * rendera ({@code api/public/mediji/{id}}); posle neuspelog rendera ostaju stari dok novi ne uspe.
 */
public record VarijantaInfo(Long id, Long grupaId, int rb, String naziv, StatusVarijante status, IzvorVarijante izvor,
                            RenderStatus renderStatus, String svgId, String resenjeSvgId, LocalDateTime izmenjeno) {
}
