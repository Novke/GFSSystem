package tri.novica.gfssystem.dto.ocenjivanje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KoeficijentTipTestaInfo {
    private Long tipTestaId;
    private String tipTestaNaziv;
    private Double maxPoena;  // koliko max poena nosi ovaj tip (za normalizaciju)
}
