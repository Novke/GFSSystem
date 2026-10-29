package tri.novica.gfssystem.dto.portal;

/**
 * Predmet koji student sluša, za tab "Predmeti" (spec studentskog dela 12.2): prisutan na {@code prisutan} od
 * {@code predavanja} (spisak iz 5.2), broj tipova otvorenih za vežbanje i da li je Novica objavio predlog ocene.
 */
public record PredmetStudenta(Long id, String naziv, int prisutan, int predavanja, int tipovaZaVezbanje,
                              boolean ocenaObjavljena) {
}
