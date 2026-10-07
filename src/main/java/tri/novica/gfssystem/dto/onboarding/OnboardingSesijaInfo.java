package tri.novica.gfssystem.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingSesijaInfo {
    private Long id;
    private String token;
    private GrupaInfo grupa;
    private boolean aktivna;
    private boolean otvorena;
    private LocalDateTime kreirano;
    private LocalDateTime istice;
    private int maxPrijava;
    private long brojPrijava;
    private long brojNaCekanju;
    private String napomena;
}
