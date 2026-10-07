package tri.novica.gfssystem.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.StatusPrijave;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrijavaInfo {
    private Long id;
    private String ime;
    private String prezime;
    private String indeks;
    private int godina;
    private String email;
    private String brojTelefona;
    private LocalDate datumRodjenja;
    private String opstina;
    private StatusPrijave status;
    private LocalDateTime podneto;
    private LocalDateTime obradjeno;
    private Long studentId;
    private String napomena;
}
