package tri.novica.gfssystem.entity.prijava;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.lti.LtiRegistracija;

import java.time.LocalDateTime;

/**
 * Veza studenta sa nalogom na spoljnoj platformi (Moodle LTI: {@code izdavac} = {@code iss}, {@code subjekat} =
 * {@code sub}); trojka (vrsta, izdavač, subjekat) je jedinstvena. Brisanje registracije ostavlja vezu
 * ({@code registracija} postaje null), brisanje studenta je briše.
 */
@Entity
@Table(name = "spoljni_identiteti",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vrsta", "izdavac", "subjekat"}))
@Getter @Setter @NoArgsConstructor
public class SpoljniIdentitet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Student student;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private VrstaIdentiteta vrsta;
    @Column(nullable = false, length = 255)
    private String izdavac;
    @Column(nullable = false, length = 255)
    private String subjekat;
    @ManyToOne(fetch = FetchType.LAZY)
    private LtiRegistracija registracija;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    private LocalDateTime poslednjiUlaz;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof SpoljniIdentitet that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return SpoljniIdentitet.class.hashCode();
    }
}
