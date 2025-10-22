package tri.novica.gfssystem.dto.ocenjivanje;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KoeficijentTipTestaCmd {
    @NotNull
    private Long tipTestaId;
    private Double maxPoena;  // koliko max poena nosi ovaj tip (za normalizaciju)
}
