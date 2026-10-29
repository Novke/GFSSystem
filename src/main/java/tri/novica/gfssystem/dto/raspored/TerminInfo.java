package tri.novica.gfssystem.dto.raspored;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Termin za nastavnika ({@code GET /rasporedi/termini}): najave sa imenima i razlozima (samo nastavnik; razlog nikad u
 * mejl), otkazivanje, da li je počeo i postojeće predavanje istog predmeta, grupe i datuma ({@code predavanjeId}, ili null).
 */
public record TerminInfo(Long stavkaId, Long rasporedId, LocalDate datum, LocalTime pocetak, LocalTime kraj,
                         PredmetInfo predmet, GrupaInfo grupa, boolean otkazan, String napomena, boolean poceo,
                         int brojNajava, int prag, List<NajavaInfo> najave, Long predavanjeId) {
}
