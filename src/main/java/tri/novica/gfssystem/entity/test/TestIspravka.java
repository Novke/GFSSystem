package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Ispravka ključa jednog polja jedne varijante na testu. {@code vrednost}: PRIHVATI ključ grupisanja odgovora, KLJUC JSON
 * {@code OdgovorPolja}, PONISTI {@code null}. Vraćena ispravka ostaje red sa {@code vracena}.
 */
@Entity
@Table(name = "test_ispravke")
@Getter @Setter @NoArgsConstructor
public class TestIspravka {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "test_id", nullable = false)
    private Long testId;
    @Column(name = "varijanta_id", nullable = false)
    private Long varijantaId;
    @Column(nullable = false, length = 40)
    private String polje;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private VrstaIspravke vrsta;
    @Column(columnDefinition = "text")
    private String vrednost;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    private LocalDateTime vracena;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TestIspravka that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TestIspravka.class.hashCode();
    }
}
