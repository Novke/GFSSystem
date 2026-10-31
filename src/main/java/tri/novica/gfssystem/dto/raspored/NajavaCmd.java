package tri.novica.gfssystem.dto.raspored;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Najava ili povlačenje (student iz kolačića, nikad iz tela); {@code razlog} do 200 znakova, ne mora, povlačenje ga ne čita. */
public record NajavaCmd(@NotNull(message = "Termin je obavezan.") Long stavkaId,
                        @NotNull(message = "Datum je obavezan.") LocalDate datum,
                        String razlog) {
}
