package tri.novica.gfssystem.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOnboardingCmd {
    private Boolean aktivna;
    private Integer isticeZaDana;
}
