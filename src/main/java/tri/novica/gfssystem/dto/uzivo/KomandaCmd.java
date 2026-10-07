package tri.novica.gfssystem.dto.uzivo;

/** Komanda; {@code vrednost} samo za {@code IDI_NA} (indeks slajda od 0, -1 = prijava). */
public record KomandaCmd(TipKomande tip, Integer vrednost) {
}
