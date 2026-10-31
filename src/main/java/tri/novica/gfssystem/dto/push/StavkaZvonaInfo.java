package tri.novica.gfssystem.dto.push;

import tri.novica.gfssystem.entity.push.VrstaStudentskogObavestenja;

import java.time.LocalDateTime;

/** Jedna stavka zvona studenta: isti sadržaj kao push poruka tog događaja ({@code putanja} relativna, kao klik na push). */
public record StavkaZvonaInfo(Long id, VrstaStudentskogObavestenja vrsta, String naslov, String tekst, String putanja,
                              LocalDateTime kreirano) {
}
