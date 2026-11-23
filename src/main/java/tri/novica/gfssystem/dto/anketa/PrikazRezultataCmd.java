package tri.novica.gfssystem.dto.anketa;

import jakarta.validation.constraints.NotNull;

/** Rezultat ankete studentima i projektoru ({@code PUT /ankete/{id}/rezultat}). */
public record PrikazRezultataCmd(@NotNull(message = "Izaberi da li se rezultat prikazuje.") Boolean prikazan) {
}
