package tri.novica.gfssystem.entity.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Izbori studenta na tabu "Ja" (jedan red po studentu, tek kad nešto promeni; bez reda važe podrazumevane vrednosti).
 * {@code mejlOtkazanCas}: da li mu se šalje mejl kad je čas otkazan ili ipak vraćen (podrazumevano da).
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
    @Column(nullable = false)
    private LocalDateTime izmenjeno;
}
