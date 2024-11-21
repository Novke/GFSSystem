package tri.novica.gfssystem.dto.test;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTestCmd {
    @NotNull(message = "Nije postavljen datum")
    private LocalDate datum;
    @NotNull(message = "Nije postavljen maksimalan broj poena")
    @Min(value = 1, message = "Maksimalan broj poena mora biti veci od 0")
    private Integer maxPoena;
    @NotNull(message = "Nije postavljen tip testa")
    private Long tipTestaId;
}
