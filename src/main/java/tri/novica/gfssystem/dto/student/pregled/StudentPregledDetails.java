package tri.novica.gfssystem.dto.student.pregled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPregledDetails {
    private Long id;
    private String ime;
    private String prezime;
    private String indeks;
    private String grupa;
    private List<StudentPregledAktivnostInfo> aktivnosti;
    private List<StudentPregledDomaciInfo> uradjeniDomaci;
    private List<StudentPregledTestInfo> polaganja;
}
