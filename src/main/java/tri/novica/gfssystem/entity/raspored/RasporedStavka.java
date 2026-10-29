package tri.novica.gfssystem.entity.raspored;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;

import java.time.LocalTime;

/**
 * Jedna linija rasporeda: predmet i grupa svakog {@code dan}-a (ISO, 1 = ponedeljak ... 7 = nedelja) od {@code pocetak} do
 * {@code kraj}, dok važi period. Bez jedinstvenog ključa u bazi (duplikate proverava {@code RasporedService} pod bravom perioda).
 */
@Entity
@Table(name = "raspored_stavke")
@Getter @Setter @NoArgsConstructor
public class RasporedStavka {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Raspored raspored;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Predmet predmet;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Grupa grupa;
    /** ISO dan u nedelji, 1-7; kolona je tinyint. */
    @JdbcTypeCode(SqlTypes.TINYINT) @Column(nullable = false)
    private int dan;
    @Column(nullable = false)
    private LocalTime pocetak;
    @Column(nullable = false)
    private LocalTime kraj;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof RasporedStavka that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return RasporedStavka.class.hashCode();
    }
}
