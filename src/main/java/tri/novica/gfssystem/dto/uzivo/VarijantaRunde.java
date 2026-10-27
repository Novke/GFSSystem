package tri.novica.gfssystem.dto.uzivo;

/**
 * Izvučena varijanta trenutne runde u konzoli: slike, koliko je dodeljeno, koliko je odgovorilo i koliko tačno, i nivo iz
 * snimka ({@code null} za rundu od pre nivoa varijante).
 */
public record VarijantaRunde(Long varijantaId, String naziv, String svgId, String resenjeSvgId, int dodeljeno,
                             int odgovorilo, int tacnih, Integer nivo) {
}
