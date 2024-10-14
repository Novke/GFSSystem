package tri.novica.gfssystem.dto.predavanje;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PredavanjeGrupaInfo {
    private Long id;
    private String naziv;
    private Integer godinaUpisa;
}
