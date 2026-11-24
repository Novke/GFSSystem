package tri.novica.gfssystem.entity.fajlovi;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;

import java.time.LocalDateTime;

/**
 * Kratak kod (1000-9999) za {@code /f/<kod>} na času, bez prijave (spec fajlova, odeljak 4). Tačno jedno od
 * {@code predavanje} (kod predavanja: sve prezentacije izvođene na njemu) i {@code izvodjenje} (izvođenje bez predavanja)
 * je popunjeno. {@code aktivanKod} je {@code kod} dok kod važi, {@code null} kad je oslobođen (UNIQUE samo među važećima).
 * Upis i obnavljanje samo kroz {@code KodoviFajlova}.
 */
@Entity
@Table(name = "kodovi_fajlova")
@Getter @Setter @NoArgsConstructor
public class KodFajlova {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, columnDefinition = "char(4)")
    private String kod;
    @Column(columnDefinition = "char(4)")
    private String aktivanKod;
    @ManyToOne(fetch = FetchType.LAZY)
    private Predavanje predavanje;
    @ManyToOne(fetch = FetchType.LAZY)
    private Izvodjenje izvodjenje;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime vaziDo;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof KodFajlova that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return KodFajlova.class.hashCode();
    }
}
