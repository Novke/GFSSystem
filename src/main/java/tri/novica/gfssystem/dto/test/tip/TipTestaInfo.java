package tri.novica.gfssystem.dto.test.tip;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TipTestaInfo {

    private Long id;

    private String naziv;

    private Boolean aktivan;

    public TipTestaInfo(Long id, String naziv) {
        this(id, naziv, null);
    }
}
