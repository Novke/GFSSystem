package tri.novica.gfssystem.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PodnetaPrijavaInfo {
    private Long id;
    /** Link za WhatsApp grupu generacije, ili null; student ga vidi tek posle uspešne prijave (ne u {@code GET}). */
    private String waLink;
}
