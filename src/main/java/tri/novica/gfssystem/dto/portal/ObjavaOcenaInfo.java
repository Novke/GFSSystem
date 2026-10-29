package tri.novica.gfssystem.dto.portal;

import java.time.LocalDateTime;

/** Stanje objave predloga ocena za predmet i grupu (nastavnik): {@code objavljeno=null} = nije objavljeno. */
public record ObjavaOcenaInfo(Long predmetId, Long grupaId, LocalDateTime objavljeno, long studenata) {
}
