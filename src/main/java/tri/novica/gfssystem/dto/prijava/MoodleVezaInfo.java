package tri.novica.gfssystem.dto.prijava;

import java.time.LocalDateTime;

/** Moodle veza studenta za karticu: da li je povezan i kad je poslednji put ušao kroz Moodle (bez izdavača i subjekta). */
public record MoodleVezaInfo(boolean povezan, LocalDateTime poslednjiUlaz) {
}
