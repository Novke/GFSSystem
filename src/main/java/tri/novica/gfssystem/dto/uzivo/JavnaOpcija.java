package tri.novica.gfssystem.dto.uzivo;

/** Opcija na telefonu: id (za dugme) i tekst samo kad je tekst dozvoljen, inače {@code null}. */
public record JavnaOpcija(Long id, String tekst) {
}
