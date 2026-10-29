package tri.novica.gfssystem.dto.portal;

import tri.novica.gfssystem.entity.TipAktivnosti;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Jedno predavanje u istoriji studenta (spec 5.2): datum, redni broj u svojoj grupi, tema, naziv grupe (drugačija od
 * sopstvene kad student ponavlja predmet), sopstvena aktivnost ({@code null} = nije bio) i vreme čekiranja. Bez napomena i
 * bodova.
 */
public record PredavanjeStudenta(Long id, LocalDate datum, int redniBroj, String tema, String grupa, boolean svojaGrupa,
                                 TipAktivnosti aktivnost, LocalDateTime cekiranU) {
}
