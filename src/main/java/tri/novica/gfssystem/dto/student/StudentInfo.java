package tri.novica.gfssystem.dto.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
    private String email;
    private LocalDate datumRodjenja;
    private String opstina;
}
