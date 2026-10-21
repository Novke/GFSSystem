package tri.novica.gfssystem.entity.prijava;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Student;

import java.time.LocalDateTime;

/**
 * Jednokratan kod za prijavu studenta (6 cifara, 10 min, najviše 5 pogrešnih unosa). U bazi je samo
 * {@code SHA-256(so + kod)} (hex); {@code iskoriscen} ili {@code ponisten} != null = više ne važi.
 */
@Entity
@Table(name = "kodovi_za_prijavu")
@Getter @Setter @NoArgsConstructor
@DynamicUpdate
public class KodZaPrijavu {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Student student;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 64)
    private String kodHes;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 32)
    private String so;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime istice;
    @Column(nullable = false)
    private int pogresni;
    private LocalDateTime iskoriscen;
    private LocalDateTime ponisten;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof KodZaPrijavu that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return KodZaPrijavu.class.hashCode();
    }
}
