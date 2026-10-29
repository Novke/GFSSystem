package tri.novica.gfssystem.dto.portal;

import jakarta.validation.constraints.NotNull;

/**
 * Podešavanja studentskog naloga (tab "Ja"), isti oblik za čitanje i izmenu: {@code mejlOtkazanCas} = javi mi mejlom kad je
 * čas otkazan (i kad je otkazan čas ipak vraćen).
 */
public record PodesavanjaNaloga(@NotNull(message = "Izaberi da li želiš mejl.") Boolean mejlOtkazanCas) {
}
