package tri.novica.gfssystem.service.uzivo;

/** Opcija u {@link PitanjeSnimak}u (kopija {@code PitanjeOpcija} u trenutku otvaranja runde). */
public record OpcijaSnimak(Long id, int rb, String tekst, boolean tacna) {
}
