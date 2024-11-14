package tri.novica.gfssystem.dto.test;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.TestGrupa;

import java.time.LocalDate;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTestCmd {

    @NotNull
    private Long tipTestaId;
    @NotNull
    private Long predmetId;
    @NotNull
    private Long grupaId;
    @NotNull
    private LocalDate datum;
    @NotNull
    private Set<TestGrupa> grupe;
    @NotNull
    private Integer maxPoena;

}
