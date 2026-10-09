package tri.novica.gfssystem.dto.uzivo;

/** Odgovor učesnika u trenutnoj rundi: {@code tacno} i {@code poeni} tek kad je tačan odgovor prikazan. */
public record LicniOdgovor(Long rundaId, boolean primljen, Boolean tacno, Integer poeni) {
}
