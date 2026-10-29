package tri.novica.gfssystem.dto.raspored;

import java.time.LocalDateTime;

/** Najava odsustva u nastavničkom pregledu termina; {@code razlog} je tekst studenta (Angular ga escape-uje). */
public record NajavaInfo(Long studentId, String ime, String prezime, String indeks, Integer godina, String razlog,
                         LocalDateTime vreme) {
}
