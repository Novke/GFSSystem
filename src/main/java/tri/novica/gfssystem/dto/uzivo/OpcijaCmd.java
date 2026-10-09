package tri.novica.gfssystem.dto.uzivo;

/** Ponuđeni odgovor; {@code tacna} se ignoriše kod ankete. */
public record OpcijaCmd(String tekst, boolean tacna) {
}
