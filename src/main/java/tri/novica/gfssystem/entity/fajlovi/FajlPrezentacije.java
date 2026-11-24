package tri.novica.gfssystem.entity.fajlovi;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.Prezentacija;
import tri.novica.gfssystem.entity.uzivo.Slajd;

import java.time.LocalDateTime;

/**
 * Fajl (DOCX, XLSX, PPTX, PDF, ZIP) okačen uz prezentaciju (spec fajlova, odeljak 2), opciono uz jedan njen slajd
 * ({@code slajd == null}: uz celu prezentaciju; brisanje slajda postavlja {@code null}). Bajtovi su u {@link Medij}
 * (vrsta {@code FAJL_PREZENTACIJE}), jedan medij pripada tačno jednom redu. {@code naziv} je ime za preuzimanje, uvek sa
 * ekstenzijom originala. Sve veze su lazy: čitaj ih u transakciji ({@code PristupFajlovima} ih inicijalizuje).
 */
@Entity
@Table(name = "fajlovi_prezentacije")
@Getter @Setter @NoArgsConstructor
public class FajlPrezentacije {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Prezentacija prezentacija;
    @ManyToOne(fetch = FetchType.LAZY)
    private Slajd slajd;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "medij_id", nullable = false, unique = true)
    private Medij medij;
    @Column(nullable = false)
    private String naziv;
    @Column(nullable = false)
    private int rb;
    /** Klodova serija upisa (MCP); {@code null} za fajlove iz editora. */
    @Column(length = 60)
    private String serija;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof FajlPrezentacije that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return FajlPrezentacije.class.hashCode();
    }
}
