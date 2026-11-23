package tri.novica.gfssystem.entity.anketa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.converter.StringListJsonConverter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pokrenuta anketa ({@code ankete}, spec brze ankete 3): pitanje i odgovori su snimak u trenutku pokretanja. Stanje se
 * računa ({@code AnketaPravila}), ništa ga ne zatvara po rasporedu: otvorena je dok {@code zatvoreno} nije upisano, rok nije
 * prošao i, za {@link PublikaAnkete#NA_CASU}, predavanje nije završeno. Veze su obični id-jevi: FK u bazi rade
 * {@code SET NULL} (šablon, predavanje) i {@code CASCADE} (grupa).
 */
@Entity
@Table(name = "ankete")
@Getter @Setter @NoArgsConstructor
public class Anketa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sablon_id")
    private Long sablonId;
    @Column(nullable = false, length = 300)
    private String pitanje;
    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> opcije;
    @Column(name = "vise_odgovora", nullable = false)
    private boolean viseOdgovora;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PublikaAnkete publika;
    @Column(name = "predavanje_id")
    private Long predavanjeId;
    @Column(name = "grupa_id", nullable = false)
    private Long grupaId;
    @Column(nullable = false)
    private LocalDateTime otvoreno;
    private LocalDateTime rok;
    private LocalDateTime zatvoreno;
    @Column(name = "rezultat_prikazan", nullable = false)
    private boolean rezultatPrikazan;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private IzvorAnkete izvor = IzvorAnkete.NASTAVNIK;
}
