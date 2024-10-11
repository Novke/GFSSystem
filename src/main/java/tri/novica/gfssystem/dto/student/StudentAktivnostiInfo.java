package tri.novica.gfssystem.dto.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.TipAktivnosti;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAktivnostiInfo {

    private TipAktivnosti tip;
}
