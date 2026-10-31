package tri.novica.gfssystem.entity.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Objava predloga ocena za predmet i grupu (spec studentskog dela 12.4): dok red ne postoji, studenti te grupe ne vide
 * ništa o oceni. Snimci po studentu su u {@link ObjavljenaOcena} (brišu se sa objavom, FK {@code ON DELETE CASCADE}).
 */
@Entity
@Table(name = "objave_ocena")
@Getter @Setter @NoArgsConstructor
public class ObjavaOcena {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "predmet_id", nullable = false)
    private Long predmetId;
    @Column(name = "grupa_id", nullable = false)
    private Long grupaId;
    @Column(nullable = false)
    private LocalDateTime objavljeno;
}
