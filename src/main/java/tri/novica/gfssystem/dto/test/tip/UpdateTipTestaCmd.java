package tri.novica.gfssystem.dto.test.tip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Izmena tipa testa ({@code PUT /test/tip/{id}}): preimenovanje i (de)aktivacija. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTipTestaCmd {
    @NotBlank(message = "Naziv tipa testa je obavezan.")
    @Size(max = 60, message = "Naziv tipa testa može imati najviše 60 znakova.")
    private String naziv;
    @NotNull(message = "Aktivnost tipa testa je obavezna.")
    private Boolean aktivan;
}
