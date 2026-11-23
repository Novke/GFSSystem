package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.PozadinaCrteza;

import java.time.LocalDateTime;
import java.util.List;

/** Crtež istog slajda iz ranijeg izvođenja (konzola: "Ranije si na ovom slajdu crtao"); {@code grupa} je naziv ili {@code null}. */
public record RanijiCrtez(Long izvodjenjeId, LocalDateTime pocetak, String grupa, PozadinaCrteza pozadina,
                          List<ElementCrteza> elementi) {
}
