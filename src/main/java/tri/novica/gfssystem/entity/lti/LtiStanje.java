package tri.novica.gfssystem.entity.lti;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * OIDC stanje između LTI login initiation i launch-a (10 min, jednokratno): heš {@code state}, {@code nonce} i heš
 * kolačića {@code gfs_lti} koji launch mora doneti. {@code iskorisceno} != null = potrošeno.
 */
@Entity
@Table(name = "lti_stanja")
@Getter @Setter @NoArgsConstructor
public class LtiStanje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 64, unique = true)
    private String stanjeHes;
    @Column(nullable = false, length = 64)
    private String nonce;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 64)
    private String kolacicHes;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private LtiRegistracija registracija;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime istice;
    private LocalDateTime iskorisceno;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof LtiStanje that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return LtiStanje.class.hashCode();
    }
}
