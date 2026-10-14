package tri.novica.gfssystem.dto.uzivo;

/**
 * Trenutna runda u nastavničkom stanju. {@code rokMs} (epoch ms) dok tajmer radi, {@code preostaloMs} dok je pauziran;
 * oba su {@code null} kad je runda zatvorena ili bez tajmera.
 */
public record RundaInfo(Long id, int redniBroj, Long rokMs, Long preostaloMs, boolean tajmerRadi) {
}
