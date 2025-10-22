package tri.novica.gfssystem.dto.student.pregled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPregledTestInfo {
    private Long id;
    private Long testId;
    private Double ostvareniPoeni;
    private Boolean polozio;
    private boolean prepisivao;
    private String napomene;
    private LocalDate datum;
    private TipTestaInfo tipTesta;
}
