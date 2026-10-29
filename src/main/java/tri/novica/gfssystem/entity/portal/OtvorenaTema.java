package tri.novica.gfssystem.entity.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Tip (čvor banke PREDAVANJE) otvoren za vežbanje grupi studenata (spec studentskog dela 5.3, 12.3): jedan red po (grupa,
 * čvor). Automatski zapisi ({@link IzvorOtvaranja#CAS}) su uvek listovi izvučenih varijanti i nastaju na kraju izvođenja;
 * ručni mogu biti i unutrašnji čvor (otvoreno je celo podstablo). Id-jevi bez veza, jer se čitaju samo kroz upite.
 */
@Entity
@Table(name = "otvorene_teme")
@Getter @Setter @NoArgsConstructor
public class OtvorenaTema {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "grupa_id", nullable = false)
    private Long grupaId;
    @Column(name = "grupa_zadataka_id", nullable = false)
    private Long temaId;
    @Column(nullable = false)
    private LocalDateTime prviPut;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private IzvorOtvaranja izvor;
    @Column(name = "izvodjenje_id")
    private Long izvodjenjeId;
}
