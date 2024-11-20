package tri.novica.gfssystem.dto.test;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.TestGrupa;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidentirajPolaganjeCmd {
    @NotNull(message = "Nije odabran student!")
    private Long studentId;
    @NotNull(message = "Nije odabrana grupa!")
    private TestGrupa grupa;
    @NotNull(message = "Nisu postavljeni ostvareni poeni!")
    private Integer ostvareniPoeni;
    private Boolean prepisivao = false;
    private String napomene;
}
