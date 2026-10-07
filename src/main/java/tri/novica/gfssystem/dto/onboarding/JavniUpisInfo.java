package tri.novica.gfssystem.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JavniUpisInfo {
    private String grupaNaziv;
    private Integer godinaUpisa;
    private boolean otvorena;
    private LocalDateTime istice;
}
