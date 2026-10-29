package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Termin rasporeda na početnoj: broj najava (bez imena), otkazivanje i postojeće predavanje istog predmeta, grupe i datuma
 * ({@code predavanjeId}, ili null).
 */
public record TerminKratkoInfo(Long stavkaId, LocalDate datum, LocalTime pocetak, LocalTime kraj, PredmetInfo predmet,
                               GrupaInfo grupa, boolean otkazan, String napomena, long brojNajava, Long predavanjeId) {
}
