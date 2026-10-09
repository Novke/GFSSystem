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
    /** Prag prolaza testa u poenima; null = test nema prag. */
    private Integer pragProlaza;
    /** Prolaz po pravilu {@code Prolaz} (poeni >= prag, bez prepisivanja), računa server; null kad test nema prag. */
    private Boolean polozeno;
    private boolean prepisivao;
    private String napomene;
    private LocalDate datum;
    private TipTestaInfo tipTesta;
}
