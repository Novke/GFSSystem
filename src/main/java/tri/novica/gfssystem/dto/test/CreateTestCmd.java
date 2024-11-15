package tri.novica.gfssystem.dto.test;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTestCmd {

    private Long tipTestaId;
    private String novTipTesta;
    @NotNull(message = "Predmet nije izabran")
    private Long predmetId;
    @NotNull(message = "Grupa nije izabrana")
    private Long grupaId;
    @NotNull(message = "Datum nije izabran")
    private LocalDate datum;
    @NotNull
    @Min(value = 1, message = "Mora postojati barem 1 grupa")
    private Integer brojGrupa;
    @NotNull
    @Min(value = 1, message = "Max poena ne moze biti manje od 1")
    @Max(value = 100, message = "Max poena ne moze biti vece od 100")
    private Integer maxPoena;

}
