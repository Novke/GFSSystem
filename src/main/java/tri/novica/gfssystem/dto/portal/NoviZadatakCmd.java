package tri.novica.gfssystem.dto.portal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Novi zadatak za vežbanje: tip (list otvoren grupi studenta) i nivo 1-3 ({@code null} = svi nivoi). */
public record NoviZadatakCmd(@NotNull(message = "Izaberi tip zadatka.") Long tipId,
                             @Min(value = 1, message = "Nivo je od 1 do 3.") @Max(value = 3, message = "Nivo je od 1 do 3.") Integer nivo) {
}
