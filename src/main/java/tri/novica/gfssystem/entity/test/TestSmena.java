package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Smena online testa (u fazi 1 tačno jedna, {@code rb = 1}): ulaz otvoren, pokrenuta, zajednički {@code rok}
 * (pokretanje + trajanje) i zatvaranje. {@code ulazniKod} (4 cifre) je opcion i ne menja se tokom smene.
 */
@Entity
@Table(name = "test_smene")
@Getter @Setter @NoArgsConstructor
public class TestSmena {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "test_id", nullable = false)
    private Long testId;
    @Column(nullable = false)
    private int rb;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 4)
    private String ulazniKod;
    private LocalDateTime ulazOtvoren;
    private LocalDateTime pokrenuta;
    private LocalDateTime rok;
    private LocalDateTime zatvorena;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TestSmena that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TestSmena.class.hashCode();
    }
}
