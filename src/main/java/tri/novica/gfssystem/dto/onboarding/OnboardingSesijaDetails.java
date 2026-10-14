package tri.novica.gfssystem.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingSesijaDetails {
    private OnboardingSesijaInfo sesija;
    private List<PrijavaInfo> prijave;
    private String poruka;
}
