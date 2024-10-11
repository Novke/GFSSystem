package tri.novica.gfssystem.dto.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentDetails {
    private Long id;
    private String ime;
    private String prezime;
    private int godina;
    private String indeks;
    private String brojTelefona;
    private StudentGrupaInfo grupa;
    private List<StudentAktivnostiInfo> aktivnosti;
    private List<StudentUradjenDomaciInfo> uradjeniDomaci;
}
