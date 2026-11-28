package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Izvor stavke (čvor banke); {@code obrisan} kad čvora više nema u banci TEST predmeta. */
public record IzvorStavke(Long grupaId, String naziv, List<String> putanja, boolean obrisan) {
}
