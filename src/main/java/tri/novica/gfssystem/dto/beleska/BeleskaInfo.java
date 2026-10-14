package tri.novica.gfssystem.dto.beleska;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BeleskaInfo {
    private Long id;
    private String tekst;
    private LocalDateTime kreirano;
    /** {@code null} dok se beleška nije menjala. */
    private LocalDateTime izmenjeno;
}
