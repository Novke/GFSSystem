package tri.novica.gfssystem.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOnboardingCmd {
    private Integer isticeZaDana;
    private Integer maxPrijava;
    private String napomena;
}
