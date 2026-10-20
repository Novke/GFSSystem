package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.service.uzivo.PitanjeSnimak;

/**
 * Jedna runda u pregledu izvođenja: snimak pitanja i nastavnički rezultat. {@code slajdId}/{@code rbSlajda} su
 * {@code null} kad slajda više nema; {@code procenatTacnih} je {@code null} kad pitanje nema tačan odgovor. Runda
 * Zadatak slajda ima {@code pitanje} i {@code rezultat} {@code null}, a sve u {@code zadatak} ({@link PregledZadatka}).
 */
public record RezultatPitanja(Long rundaId, Long slajdId, Integer rbSlajda, int redniBroj, PitanjeSnimak pitanje,
                              Rezultat rezultat, int brojOdgovora, Integer procenatTacnih, PregledZadatka zadatak) {

    public RezultatPitanja(Long rundaId, Long slajdId, Integer rbSlajda, int redniBroj, PitanjeSnimak pitanje,
                           Rezultat rezultat, int brojOdgovora, Integer procenatTacnih) {
        this(rundaId, slajdId, rbSlajda, redniBroj, pitanje, rezultat, brojOdgovora, procenatTacnih, null);
    }
}
