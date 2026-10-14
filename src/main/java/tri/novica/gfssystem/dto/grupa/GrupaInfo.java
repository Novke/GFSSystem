package tri.novica.gfssystem.dto.grupa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrupaInfo {
    private Long id;
    private String naziv;
    private Integer godinaUpisa;
    private Long brojStudenata;

    public GrupaInfo(Long id, String naziv, Integer godinaUpisa) {
        this(id, naziv, godinaUpisa, null);
    }
}
