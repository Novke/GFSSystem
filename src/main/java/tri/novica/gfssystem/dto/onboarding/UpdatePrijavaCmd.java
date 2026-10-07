package tri.novica.gfssystem.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePrijavaCmd implements PoljaPrijave {
    private String ime;
    private String prezime;
    private String indeks;
    private Integer godina;
    private String email;
    private String brojTelefona;
    private LocalDate datumRodjenja;
    private String opstina;
}
