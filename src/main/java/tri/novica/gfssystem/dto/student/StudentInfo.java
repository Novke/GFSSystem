package tri.novica.gfssystem.dto.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentInfo {
    private Long id;
    private String ime;
    private String prezime;
    private int godina;
    private String indeks;
    private String brojTelefona;
}
