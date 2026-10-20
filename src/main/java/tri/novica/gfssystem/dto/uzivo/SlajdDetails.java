package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.uzivo.platno.Platno;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

/**
 * Slajd za nastavnika (editor, konzola); {@code pitanje} je {@code null} za INFO, {@code platno} osim za PLATNO,
 * {@code prelaz} kad slajd nema prelaz (faza 5), {@code zadatak} osim za ZADATAK.
 */
public record SlajdDetails(Long id, int rb, TipSlajda tip, String naslov, String sadrzaj, MedijInfo slika,
                           String beleske, boolean postepeno, PitanjeDetails pitanje, Platno platno, Prelaz prelaz,
                           ZadatakSlajdaInfo zadatak) {

    /** Slajd bez Zadatka (oblik pre slajda Zadatak). */
    public SlajdDetails(Long id, int rb, TipSlajda tip, String naslov, String sadrzaj, MedijInfo slika, String beleske,
                        boolean postepeno, PitanjeDetails pitanje, Platno platno, Prelaz prelaz) {
        this(id, rb, tip, naslov, sadrzaj, slika, beleske, postepeno, pitanje, platno, prelaz, null);
    }
}
