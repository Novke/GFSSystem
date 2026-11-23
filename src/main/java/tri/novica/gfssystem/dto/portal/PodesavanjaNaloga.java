package tri.novica.gfssystem.dto.portal;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;

/**
 * Podešavanja studentskog naloga (tab "Ja"), isti oblik za čitanje i izmenu: {@code mejlOtkazanCas} = javi mi mejlom kad je
 * čas otkazan (i kad je otkazan čas ipak vraćen). Prekidači push-a ({@code push*}: podsetnik dan ranije, čas otkazan ili
 * ipak se održava, ocene objavljene, posle časa) i zvona ({@code zvono*}: ocene, posle časa; otkazan čas je u zvonu uvek)
 * važe za sve uređaje studenta, podrazumevano uključeni; {@code pushAnkete} (V27) = push brze ankete na času i za grupu.
 * {@code GET} vraća sva polja; u {@code PUT}-u su nova polja opciona ({@code null} = ne menja, stariji front šalje samo
 * {@code mejlOtkazanCas}, a front pre ankete ne šalje {@code pushAnkete}).
 */
public record PodesavanjaNaloga(@NotNull(message = "Izaberi da li želiš mejl.") Boolean mejlOtkazanCas,
                                Boolean pushPodsetnik, Boolean pushOtkazanCas, Boolean pushOcene, Boolean pushPosleCasa,
                                Boolean zvonoOcene, Boolean zvonoPosleCasa, Boolean pushAnkete) {

    @JsonCreator
    public PodesavanjaNaloga {
    }

    /** Oblik pre brze ankete: {@code pushAnkete} se ne menja. */
    public PodesavanjaNaloga(Boolean mejlOtkazanCas, Boolean pushPodsetnik, Boolean pushOtkazanCas, Boolean pushOcene,
                             Boolean pushPosleCasa, Boolean zvonoOcene, Boolean zvonoPosleCasa) {
        this(mejlOtkazanCas, pushPodsetnik, pushOtkazanCas, pushOcene, pushPosleCasa, zvonoOcene, zvonoPosleCasa, null);
    }

    /** Samo mejl (stariji oblik zahteva): ostali prekidači se ne menjaju. */
    public PodesavanjaNaloga(Boolean mejlOtkazanCas) {
        this(mejlOtkazanCas, null, null, null, null, null, null, null);
    }
}
