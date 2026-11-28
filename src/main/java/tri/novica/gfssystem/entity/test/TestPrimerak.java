package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.TestGrupa;

import java.time.LocalDateTime;

/**
 * Test jednog studenta ({@code UNIQUE(test_id, student_id)}). {@code poeni} je zbir zadataka posle ocene;
 * {@code prepisivao} je izvor oznake dok polaganje još ne postoji (prepisuje se u {@code Polaganje}).
 * {@code aktivniUredjajId}: uređaj na kom je test otvoren (jedan u isto vreme, "Nastavi ovde" ga preuzima).
 */
@Entity
@Table(name = "test_primerci")
@Getter @Setter @NoArgsConstructor
public class TestPrimerak {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "test_id", nullable = false)
    private Long testId;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Student student;
    @Column(name = "smena_id", nullable = false)
    private Long smenaId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 1)
    private TestGrupa grupaTesta;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private StanjePrimerka stanje = StanjePrimerka.DODELJEN;
    private LocalDateTime usao;
    @Column(nullable = false)
    private int produzenjeMin;
    private LocalDateTime predato;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 20)
    private NacinPredaje nacinPredaje;
    private Double poeni;
    @Column(nullable = false)
    private boolean prepisivao;
    @Column(name = "aktivni_uredjaj_id")
    private Long aktivniUredjajId;
    @Column(length = 64)
    private String ip;
    @Column(length = 500)
    private String napomena;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TestPrimerak that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TestPrimerak.class.hashCode();
    }
}
