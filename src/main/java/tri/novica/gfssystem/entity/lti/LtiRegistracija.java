package tri.novica.gfssystem.entity.lti;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registracija alata na LTI 1.3 platformi (Moodle; svaki predmet registruje alat posebno). Platforma se prepoznaje po
 * trojci (izdavač {@code iss}, Client ID, Deployment ID), koja je jedinstvena. Ništa od ovoga nije tajna.
 */
@Entity
@Table(name = "lti_registracije",
        uniqueConstraints = @UniqueConstraint(columnNames = {"izdavac", "client_id", "deployment_id"}))
@Getter @Setter @NoArgsConstructor
public class LtiRegistracija {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String naziv;
    @Column(nullable = false, length = 255)
    private String izdavac;
    @Column(nullable = false, length = 255)
    private String clientId;
    @Column(nullable = false, length = 255)
    private String deploymentId;
    @Column(nullable = false, length = 500)
    private String authUrl;
    @Column(nullable = false, length = 500)
    private String jwksUrl;
    @Column(nullable = false)
    private boolean aktivna;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof LtiRegistracija that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return LtiRegistracija.class.hashCode();
    }
}
