package tri.novica.gfssystem.dto.grupa;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Izmena grupe ({@code PUT /grupe/{id}}): pun zamenski zapis, obe vrednosti su obavezne. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGrupaCmd {
    @NotBlank(message = "Naziv grupe je obavezan.")
    @Size(max = 60, message = "Naziv grupe može imati najviše 60 znakova.")
    private String naziv;
    @NotNull(message = "Godina upisa je obavezna.")
    @Min(value = 2000, message = "Godina upisa nije ispravna.")
    @Max(value = 2100, message = "Godina upisa nije ispravna.")
    private Integer godinaUpisa;
}
