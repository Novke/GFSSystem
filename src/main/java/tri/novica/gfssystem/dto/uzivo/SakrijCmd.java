package tri.novica.gfssystem.dto.uzivo;

/** Sakriva ili vraća grupu tekstualnih odgovora; {@code kljuc} je normalizovan tekst ({@code RezultatTekst.kljuc}). */
public record SakrijCmd(String kljuc, boolean sakriven) {
}
