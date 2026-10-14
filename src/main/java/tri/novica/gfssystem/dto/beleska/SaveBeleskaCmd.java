package tri.novica.gfssystem.dto.beleska;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Telo za dodavanje ({@code POST /studenti/{id}/beleske}) i izmenu ({@code PUT /beleske/{id}}) beleške. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveBeleskaCmd {
    @NotBlank(message = "Tekst beleške je obavezan.")
    @Size(max = 2000, message = "Beleška može imati najviše 2000 znakova.")
    private String tekst;
}
