package tri.novica.gfssystem.dto.student.pregled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.TipAktivnosti;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPregledAktivnostInfo {
    private Long id;
    private Long predavanjeId;
    private TipAktivnosti tip;
    private String napomene;
    private LocalDate datum;
    private String tema;
}
