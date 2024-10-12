package tri.novica.gfssystem.dto.predavanje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.TipAktivnosti;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredavanjeAktivnostInfo {
    private Long id;
    private PredavanjeStudentInfo student;
    private TipAktivnosti tip;
    private String napomene;
}
