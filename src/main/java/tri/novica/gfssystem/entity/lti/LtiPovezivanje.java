package tri.novica.gfssystem.entity.lti;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Započeto povezivanje Moodle naloga ({@code sub}) sa studentom (kolačić {@code __Host-gfs_lti_par}, 30 min, jednokratno).
 * {@code ime} je ime iz LTI claim-a kad ga platforma deli (samo za prikaz), nikad u logu.
 */
@Entity
@Table(name = "lti_povezivanja")
@Getter @Setter @NoArgsConstructor
public class LtiPovezivanje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 64, unique = true)
    private String tokenHes;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private LtiRegistracija registracija;
    @Column(nullable = false, length = 255)
    private String subjekat;
    @Column(length = 200)
    private String ime;
    @Column(nullable = false)
    private LocalDateTime istice;
    private LocalDateTime iskorisceno;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof LtiPovezivanje that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return LtiPovezivanje.class.hashCode();
    }
}
