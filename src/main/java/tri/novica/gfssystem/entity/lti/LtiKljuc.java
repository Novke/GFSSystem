package tri.novica.gfssystem.entity.lti;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RSA ključ alata (JWKS): privatni kao PKCS#8 (base64), javni kao JWK (JSON), {@code kid} jedinstven. Rotacija = nov
 * red; stari ostaje u JWKS-u dok je {@code aktivan}. Privatni deo se nikad ne loguje.
 */
@Entity
@Table(name = "lti_kljucevi")
@Getter @Setter @NoArgsConstructor
public class LtiKljuc {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64, unique = true)
    private String kid;
    @Column(columnDefinition = "text", nullable = false)
    private String privatni;
    @Column(columnDefinition = "text", nullable = false)
    private String javni;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private boolean aktivan;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof LtiKljuc that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return LtiKljuc.class.hashCode();
    }
}
