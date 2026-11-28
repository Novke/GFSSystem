package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.TestGrupa;

/**
 * Plan izvlačenja: GRUPE red po (grupa testa, stavka, mesto) sa varijantom; POJEDINACNO red po (stavka, mesto) sa
 * izabranim tipom ({@code tipId}, {@code null} kad stavka nema isti tip), {@code varijantaId} je tada {@code null}
 * (varijante se dele po primercima). {@code varijantaId} je FK RESTRICT.
 */
@Entity
@Table(name = "test_izbor")
@Getter @Setter @NoArgsConstructor
public class TestIzbor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "test_id", nullable = false)
    private Long testId;
    @Column(name = "smena_id", nullable = false)
    private Long smenaId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 1)
    private TestGrupa grupaTesta;
    @Column(name = "stavka_id", nullable = false)
    private Long stavkaId;
    @Column(nullable = false)
    private int mesto;
    @Column(name = "tip_id")
    private Long tipId;
    @Column(name = "varijanta_id")
    private Long varijantaId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TestIzbor that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TestIzbor.class.hashCode();
    }
}
