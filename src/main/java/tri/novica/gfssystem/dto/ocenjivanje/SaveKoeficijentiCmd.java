package tri.novica.gfssystem.dto.ocenjivanje;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveKoeficijentiCmd {

    // Koeficijenti za aktivnosti
    private Double koefPrisustvo = 1.0;
    private Double koefZadatak = 2.0;
    private Double koefZvezdica = 4.0;

    // Koeficijenti za domace
    private Double domaciFlat = 4.0;
    private Double domaciVarijansa = 6.0;

    // Opcije
    private Boolean koristiMaxRezultat = true;
    private Boolean prikaziZbirno = false;

    // Maksimumi za normalizaciju (ako null, ne normalizuje se)
    private Double maxAktivnost;
    private Double maxDomaci;

    // Koeficijenti po tipu testa
    @Valid
    private List<KoeficijentTipTestaCmd> koeficijentiTipova = new ArrayList<>();
}
