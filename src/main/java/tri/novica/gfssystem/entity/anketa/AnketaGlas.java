package tri.novica.gfssystem.entity.anketa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Glas jednog studenta u anketi ({@code ankete_glasovi}, {@code UNIQUE(anketa_id, student_id)}): {@code opcije} su indeksi
 * odgovora rastuće, zarezom ({@code "0,2"}), {@code vreme} je poslednja promena. Upisuje se samo upitom
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} ({@code AnketaGlasRepository.upisi}); nastavnik i MCP vide samo zbirove.
 */
@Entity
@Table(name = "ankete_glasovi")
@Getter @Setter @NoArgsConstructor
public class AnketaGlas {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "anketa_id", nullable = false)
    private Long anketaId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(nullable = false, length = 40)
    private String opcije;
    @Column(nullable = false)
    private LocalDateTime vreme;
}
