package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tri.novica.gfssystem.entity.banka.Varijanta;

import java.time.LocalDateTime;

/**
 * Varijanta zadatka dodeljena učesniku u jednoj ZADATAK rundi (najviše jedna, {@code UNIQUE(runda_id, ucesnik_id)}).
 * Nastaje pod ključem reda izvođenja; brisanje runde ili učesnika je briše (CASCADE), a varijanta sa dodelom se ne briše
 * (RESTRICT: "korišćeno na času").
 */
@Entity
@Table(name = "zadatak_dodele")
@Getter @Setter @NoArgsConstructor
public class ZadatakDodela {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private PitanjeRunda runda;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Ucesnik ucesnik;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Varijanta varijanta;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ZadatakDodela that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return ZadatakDodela.class.hashCode();
    }
}
