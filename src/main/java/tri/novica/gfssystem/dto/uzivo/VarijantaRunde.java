package tri.novica.gfssystem.dto.uzivo;

/** Izvučena varijanta trenutne runde u konzoli: slike, koliko je dodeljeno, koliko je odgovorilo i koliko tačno. */
public record VarijantaRunde(Long varijantaId, String naziv, String svgId, String resenjeSvgId, int dodeljeno,
                             int odgovorilo, int tacnih) {
}
