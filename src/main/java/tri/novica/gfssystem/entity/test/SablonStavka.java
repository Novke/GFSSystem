package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stavka šablona ("Zadatak 2"): poeni po izvučenom zadatku, broj zadataka, dozvoljeni nivoi ({@code "2,3"}, {@code null} =
 * svi), isti tip za sve, ručno bodovanje i izvori (čvorovi banke namene TEST istog predmeta; više čvorova = unija).
 * Brisanje čvora banke briše red izvora (FK CASCADE).
 */
@Entity
@Table(name = "sablon_stavke")
@Getter @Setter @NoArgsConstructor
public class SablonStavka {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private SablonTesta sablon;
    @Column(nullable = false)
    private int rb;
    @Column(nullable = false, length = 120)
    private String naziv;
    @Column(nullable = false)
    private int poeni;
    @Column(nullable = false)
    private int broj;
    @Column(length = 10)
    private String nivoi;
    @Column(nullable = false)
    private boolean istiTip = true;
    @Column(nullable = false)
    private boolean rucno;
    @ElementCollection
    @CollectionTable(name = "sablon_stavka_izvori", joinColumns = @JoinColumn(name = "stavka_id"))
    @Column(name = "grupa_zadataka_id", nullable = false)
    private Set<Long> izvori = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof SablonStavka that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return SablonStavka.class.hashCode();
    }
}
