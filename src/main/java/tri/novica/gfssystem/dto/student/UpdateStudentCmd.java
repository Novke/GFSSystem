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

/**
 * Izmena studenta ({@code PUT /studenti/{id}}): pun zamenski zapis, izostavljeno opciono polje se briše.
 * {@code grupaId} je nova grupa studenta, pa isti poziv služi i za premeštanje.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudentCmd {
    @NotNull(message = "Grupa je obavezna.")
    private Long grupaId;
    @NotBlank(message = "Ime je obavezno.")
    @Size(max = 60, message = "Ime može imati najviše 60 znakova.")
    private String ime;
    @NotBlank(message = "Prezime je obavezno.")
    @Size(max = 60, message = "Prezime može imati najviše 60 znakova.")
    private String prezime;
    @NotBlank(message = "Indeks je obavezan.")
    @Size(min = 2, max = 20, message = "Indeks mora imati od 2 do 20 znakova.")
    private String indeks;
    @NotNull(message = "Godina upisa je obavezna.")
    @Min(value = 2000, message = "Godina upisa nije ispravna.")
    @Max(value = 2100, message = "Godina upisa nije ispravna.")
    private Integer godina;
    @Email(message = "Email nije ispravan.")
    @Size(max = 255, message = "Email može imati najviše 255 znakova.")
    private String email;
    @Size(max = 20, message = "Broj telefona može imati najviše 20 znakova.")
    private String brojTelefona;
    private LocalDate datumRodjenja;
    @Size(max = 100, message = "Opština može imati najviše 100 znakova.")
    private String opstina;
}
