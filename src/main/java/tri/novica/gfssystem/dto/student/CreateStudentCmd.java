package tri.novica.gfssystem.dto.student;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateStudentCmd {
    @NotNull(message = "Grupa je obavezna.")
    private Long grupaId;
    @NotBlank(message = "Ime je obavezno.")
    @Size(max = 60, message = "Ime može imati najviše 60 znakova.")
    private String ime;
    @NotBlank(message = "Prezime je obavezno.")
    @Size(max = 60, message = "Prezime može imati najviše 60 znakova.")
    private String prezime;
    @Min(value = 2000, message = "Godina upisa nije ispravna.")
    @Max(value = 2100, message = "Godina upisa nije ispravna.")
    private int godina;
    @NotBlank(message = "Indeks je obavezan.")
    @Size(max = 20, message = "Indeks može imati najviše 20 znakova.")
    private String indeks;
    @Size(max = 20, message = "Broj telefona može imati najviše 20 znakova.")
    private String brojTelefona;
    @Email(message = "Email nije ispravan.")
    @Size(max = 120, message = "Email može imati najviše 120 znakova.")
    private String email;
    private LocalDate datumRodjenja;
    @Size(max = 100, message = "Opština može imati najviše 100 znakova.")
    private String opstina;
}
