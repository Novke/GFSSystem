package tri.novica.gfssystem.dto.ocenjivanje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KoeficijentiInfo {
    private Long id;
    private Long predmetId;

    // Koeficijenti za aktivnosti
    private Double koefPrisustvo;
    private Double koefZadatak;
    private Double koefZvezdica;

    // Koeficijenti za domace
    private Double domaciFlat;
    private Double domaciVarijansa;

    // Opcije
    private Boolean koristiMaxRezultat;
    private Boolean prikaziZbirno;

    // Maksimumi za normalizaciju (ako null, ne normalizuje se)
    private Double maxAktivnost;
    private Double maxDomaci;

    // Koeficijenti po tipu testa
    private List<KoeficijentTipTestaInfo> koeficijentiTipova = new ArrayList<>();
}
