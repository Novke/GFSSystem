package tri.novica.gfssystem.dto.portal;

import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.dto.uzivo.PitanjeDetails;
import tri.novica.gfssystem.dto.uzivo.platno.Platno;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

/**
 * Slajd u studentskom pregledu prezentacije (spec 6.1, 12.9 tačka 8): trenutna verzija, samo slajd koji je grupa videla u
 * završenom izvođenju. Nikad beleške, serija ni izvor (DTO ta polja nema). {@code pitanje} je iz snimka runde završenog
 * izvođenja sa grupom studenta i tada nosi tačan odgovor ({@code tacanPoznat}); bez takve runde trenutna verzija bez tačnog
 * odgovora ({@code tacanPoznat=false}). {@code platno} bez poteza crteža, {@code zadatak} samo za ZADATAK,
 * {@code mojOdgovor} samo za PITANJE na koje je student odgovarao prijavljen.
 */
public record SlajdPregleda(Long id, int rb, TipSlajda tip, String naslov, String sadrzaj, MedijInfo slika,
                            PitanjeDetails pitanje, Platno platno, ZadatakPregleda zadatak, OdgovorPregleda mojOdgovor,
                            boolean tacanPoznat) {
}
