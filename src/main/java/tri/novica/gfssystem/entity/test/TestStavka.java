package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Prepis stavke šablona na testu. {@code izvori} je JSON lista id-jeva čvorova banke (obrisan čvor ostaje u listi i
 * prikazuje se kao obrisan); {@code izabraniNivo} je jedan nivo izvučen za ceo test ({@code null} dok nije izvučen).
 */
@Entity
@Table(name = "test_stavke")
@Getter @Setter @NoArgsConstructor
public class TestStavka {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "test_id", nullable = false)
    private Long testId;
    @Column(nullable = false)
    private int rb;
    @Column(nullable = false, length = 120)
    private String naziv;
    @Column(nullable = false)
    private int poeni;
    @Column(nullable = false)
    private int broj;
    @Column(length = 10)
    private String nivoi;
    @Column(nullable = false)
    private boolean istiTip = true;
    @Column(nullable = false)
    private boolean rucno;
    @Column(columnDefinition = "text", nullable = false)
    private String izvori = "[]";
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer izabraniNivo;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TestStavka that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TestStavka.class.hashCode();
    }
}
