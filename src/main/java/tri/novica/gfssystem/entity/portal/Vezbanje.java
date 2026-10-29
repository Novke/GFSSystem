package tri.novica.gfssystem.entity.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Jedan izvučen zadatak pri vežbanju (spec studentskog dela 5.4, 5.7): student, varijanta i njen list ({@code temaId},
 * {@code null} kad je list obrisan). Provera i rešenje idu preko id-ja ovog reda, uvek uz id studenta iz sesije. Bez
 * bodova i aktivnosti; brisanje varijante briše i red (FK {@code ON DELETE CASCADE}).
 */
@Entity
@Table(name = "vezbanja")
@Getter @Setter @NoArgsConstructor
public class Vezbanje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "varijanta_id", nullable = false)
    private Long varijantaId;
    @Column(name = "grupa_zadataka_id")
    private Long temaId;
    @Column(nullable = false)
    private LocalDateTime izvuceno;
    private Boolean prvaProveraTacno;
    @Column(nullable = false)
    private int brojProvera;
    private Boolean tacno;
    @Column(nullable = false)
    private boolean resenjePrikazano;
}
