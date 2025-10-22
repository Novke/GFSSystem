package tri.novica.gfssystem.dto.student.pregled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPregledDomaciInfo {
    private Long id;
    private Long domaciId;
    private Double bodovi;
    private String napomene;
    private boolean prepisivanje;
    private boolean oslobodjen;
    private LocalDate datum;
    private String naslov;
}
