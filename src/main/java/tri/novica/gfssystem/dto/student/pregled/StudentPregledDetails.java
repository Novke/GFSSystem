package tri.novica.gfssystem.dto.student.pregled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPregledDetails {
    private Long id;
    private String ime;
    private String prezime;
    private int godina;
    private String indeks;
    private String brojTelefona;
    private String email;
    private LocalDate datumRodjenja;
    private String opstina;
    /** Naziv grupe; id grupe je u {@code grupaId}. Null za studenta bez grupe. */
    private String grupa;
    private Long grupaId;
    private List<StudentPregledAktivnostInfo> aktivnosti;
    private List<StudentPregledDomaciInfo> uradjeniDomaci;
    private List<StudentPregledTestInfo> polaganja;
}
