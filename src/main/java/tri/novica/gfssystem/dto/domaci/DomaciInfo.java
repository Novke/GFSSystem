package tri.novica.gfssystem.dto.domaci;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class DomaciInfo {
    private Long id;
    private String naslov;
    private String text;
    private Boolean pregledan;
    private LocalDate datum;
}
