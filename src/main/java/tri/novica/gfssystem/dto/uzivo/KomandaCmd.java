package tri.novica.gfssystem.dto.uzivo;

/**
 * Komanda; {@code vrednost} samo za {@code IDI_NA} (indeks slajda od 0, -1 = prijava) i {@code BROJ_VARIJANTI}
 * ({@code null} = kao na slajdu, 0 = sve aktivne, 1..50 = toliko).
 */
public record KomandaCmd(TipKomande tip, Integer vrednost) {
}
