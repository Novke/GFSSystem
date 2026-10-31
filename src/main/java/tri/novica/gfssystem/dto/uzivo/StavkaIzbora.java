package tri.novica.gfssystem.dto.uzivo;

import java.time.LocalDateTime;

/** Prezentacija u izboru za grupu; {@code pustenoGrupi} je početak najnovijeg izvođenja te grupe ili {@code null}. */
public record StavkaIzbora(PrezentacijaInfo prezentacija, LocalDateTime pustenoGrupi) {
}
