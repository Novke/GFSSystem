package tri.novica.gfssystem.dto.test;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.TestGrupa;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePolaganjeCmd {
    @NotNull
    private Long studentId;
    @NotNull
    private TestGrupa grupa;
    @NotNull
    private Integer ostvareniPoeni;
    @NotNull
    private Boolean polozio;
    private Boolean prepisivao = false;
}
