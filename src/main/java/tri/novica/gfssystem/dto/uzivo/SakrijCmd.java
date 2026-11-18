package tri.novica.gfssystem.dto.uzivo;

/**
 * Sakriva ili vraća grupu tekstualnih odgovora; {@code kljuc} je normalizovan tekst ({@code RezultatTekst.kljuc}). Sa
 * {@code ucesnikId} (panel učesnika) ključ je normalizovan tekst odgovora tog učesnika u rundi, a {@code kljuc} se ignoriše.
 */
public record SakrijCmd(String kljuc, boolean sakriven, Long ucesnikId) {

    public SakrijCmd(String kljuc, boolean sakriven) {
        this(kljuc, sakriven, null);
    }
}
