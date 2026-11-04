package tri.novica.gfssystem.dto.uzivo;

/** Ishod vraćanja unosa istorije; {@code obnovljen} = slajd je bio obrisan i sada ima nov id. */
public record VracanjeIzmene(boolean obnovljen, Long prezentacijaId, Long slajdId, Long izmenaId) {
}
