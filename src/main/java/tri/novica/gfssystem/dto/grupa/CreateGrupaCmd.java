package tri.novica.gfssystem.dto.grupa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGrupaCmd {
    private String naziv;
    private Integer godinaUpisa;
}
