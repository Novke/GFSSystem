package tri.novica.gfssystem.entity.prijava;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tajni ključ aplikacije u bazi (npr. {@code MASKA_HMAC}: 32 bajta, base64), pravi se pri prvoj upotrebi; naziv je
 * jedinstven. Vrednost se nikad ne loguje.
 */
@Entity
@Table(name = "kljucevi_aplikacije")
@Getter @Setter @NoArgsConstructor
public class KljucAplikacije {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40, unique = true)
    private String naziv;
    @Column(nullable = false, length = 100)
    private String vrednost;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof KljucAplikacije that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return KljucAplikacije.class.hashCode();
    }
}
