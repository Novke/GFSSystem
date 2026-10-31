package tri.novica.gfssystem.dto.portal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Zadatak za vežbanje: tip (list otvoren grupi studenta), nivo 1-3 ({@code null} = svi nivoi) i {@code novi}: {@code true}
 * je dugme "Novi zadatak" (uvek novo izvlačenje); bez njega se vraća netaknut pokušaj istog tipa i nivoa iz poslednjih 10
 * minuta, ako postoji.
 */
public record NoviZadatakCmd(@NotNull(message = "Izaberi tip zadatka.") Long tipId,
                             @Min(value = 1, message = "Nivo je od 1 do 3.") @Max(value = 3, message = "Nivo je od 1 do 3.") Integer nivo,
                             Boolean novi) {

    public NoviZadatakCmd(Long tipId, Integer nivo) {
        this(tipId, nivo, null);
    }
}
