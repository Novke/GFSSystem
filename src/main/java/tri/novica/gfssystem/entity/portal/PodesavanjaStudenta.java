package tri.novica.gfssystem.entity.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Izbori studenta na tabu "Ja" (jedan red po studentu, tek kad nešto promeni; bez reda važe podrazumevane vrednosti).
 * {@code mejlOtkazanCas}: da li mu se šalje mejl kad je čas otkazan ili ipak vraćen (podrazumevano da). Prekidači push-a
 * ({@code push*}) i zvona ({@code zvono*}) po vrsti obaveštenja (V22, podrazumevano svi uključeni) i vreme poslednjeg
 * otvaranja zvona ({@code zvonoOtvoreno}, po studentu, ne po uređaju: tačka se gasi svuda).
 */
@Entity
@Table(name = "podesavanja_studenta")
@Getter @Setter @NoArgsConstructor
public class PodesavanjaStudenta {
    @Id
    @Column(name = "student_id")
    private Long studentId;
    @Column(name = "mejl_otkazan_cas", nullable = false)
    private boolean mejlOtkazanCas = true;
    @Column(name = "push_podsetnik", nullable = false)
    private boolean pushPodsetnik = true;
    @Column(name = "push_otkazan_cas", nullable = false)
    private boolean pushOtkazanCas = true;
    @Column(name = "push_ocene", nullable = false)
    private boolean pushOcene = true;
    @Column(name = "push_posle_casa", nullable = false)
    private boolean pushPosleCasa = true;
    @Column(name = "zvono_ocene", nullable = false)
    private boolean zvonoOcene = true;
    @Column(name = "zvono_posle_casa", nullable = false)
    private boolean zvonoPosleCasa = true;
    @Column(name = "zvono_otvoreno")
    private LocalDateTime zvonoOtvoreno;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;
}
