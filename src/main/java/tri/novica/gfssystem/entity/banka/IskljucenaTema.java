package tri.novica.gfssystem.entity.banka;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Predmet;

import java.time.LocalDateTime;

/**
 * Isključena tema predmeta (spec MCP 6.2): sa {@code grupa} isključuje tu grupu i celo njeno podstablo, bez grupe je samo
 * opis teme koju Klod ne pravi. Po predmetu najviše jedna tema po grupi ({@code UNIQUE(predmet_id, grupa_id)}); brisanje
 * grupe ostavlja temu bez grupe, brisanje predmeta briše teme (strani ključevi u V12).
 */
@Entity
@Table(name = "iskljucene_teme")
@Getter @Setter @NoArgsConstructor
public class IskljucenaTema {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Predmet predmet;
    @Column(nullable = false, length = 300)
    private String opis;
    /** null = tema bez grupe (samo opis), ili je grupa u međuvremenu obrisana. */
    @ManyToOne
    private GrupaZadataka grupa;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private IzvorVarijante izvor = IzvorVarijante.RUCNO;
    @Column(length = 60)
    private String serija;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof IskljucenaTema that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return IskljucenaTema.class.hashCode();
    }
}
