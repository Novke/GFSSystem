package tri.novica.gfssystem.dto.fajlovi;

import java.time.LocalDate;
import java.util.List;

/** Odgovor {@code GET /public/fajlovi/{kod}}: {@code tema} je tema predavanja ili naziv prezentacije (izvođenje bez predavanja). */
public record FajloviCasa(String kod, String predmet, String tema, LocalDate datum, List<FajlCasa> fajlovi) {
}
