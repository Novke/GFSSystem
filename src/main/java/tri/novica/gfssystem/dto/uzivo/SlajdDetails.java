package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.uzivo.platno.Platno;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

/**
 * Slajd za nastavnika (editor, konzola); {@code pitanje} je {@code null} za INFO, {@code platno} osim za PLATNO,
 * {@code prelaz} kad slajd nema prelaz (faza 5).
 */
public record SlajdDetails(Long id, int rb, TipSlajda tip, String naslov, String sadrzaj, MedijInfo slika,
                           String beleske, boolean postepeno, PitanjeDetails pitanje, Platno platno, Prelaz prelaz) {
}
