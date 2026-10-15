package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.uzivo.platno.Potez;

import java.util.List;

/**
 * Potezi jednog CRTEZ sloja (faza 5). Snimak izvođenja nosi platno bez poteza (do 4 snimka u sekundi), pa ih prozor
 * publike i konzola traže posebno, jednom po slajdu; {@code sirina}/{@code visina} su veličina sloja za koju potezi važe.
 */
public record PoteziSloja(String slojId, String medijId, Double sirina, Double visina, List<Potez> potezi) {
}
