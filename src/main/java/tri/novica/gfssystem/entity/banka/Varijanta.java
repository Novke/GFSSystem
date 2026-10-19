package tri.novica.gfssystem.entity.banka;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.uzivo.Medij;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Varijanta zadatka u grupi: LaTeX tekst i rešenje, odgovori, rezultat rendera (SVG mediji) i prilozi. */
@Entity
@Table(name = "varijante")
@Getter @Setter @NoArgsConstructor
public class Varijanta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private GrupaZadataka grupa;
    /** Redni broj u grupi. */
    @Column(nullable = false)
    private int rb;
    @Column(length = 120)
    private String naziv;
    @Column(columnDefinition = "mediumtext", nullable = false)
    private String tekst;
    @Column(columnDefinition = "mediumtext")
    private String resenje;
    /** Vrednosti po poljima šeme grupe: JSON objekat {@code kljuc -> vrednost}; običan tekst u bazi, čita/piše ga servis. */
    @Column(columnDefinition = "text", nullable = false)
    private String odgovori = "{}";
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private StatusVarijante status;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private IzvorVarijante izvor;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private RenderStatus renderStatus;
    @ManyToOne
    private Medij renderSvg;
    @ManyToOne
    private Medij renderResenjeSvg;
    @Column(columnDefinition = "text")
    private String renderGreska;
    /** SHA-256 (heksadecimalno) nad preambulom, tekstom, rešenjem i prilozima poslednjeg rendera; null = ponoviti render. */
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64)
    private String renderHash;
    /**
     * Bila je bar jednom odobrena ({@code odobri}); vraćanje u nacrt ga ne briše. Klod takvu ne menja na mestu (pravi
     * kopiju) i ne briše.
     */
    @Column(nullable = false)
    private boolean ikadOdobrena;
    /** Original koji ova kopija (Klodova izmena tuđe ili odobrene varijante) zamenjuje; brisanje originala ga postavlja na null. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Varijanta zamenjuje;
    /** Klodova serija upisa; null za ručno unete varijante. */
    @Column(length = 60)
    private String serija;
    @OneToMany(mappedBy = "varijanta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ime")
    private List<VarijantaPrilog> prilozi = new ArrayList<>();
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Varijanta that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Varijanta.class.hashCode();
    }
}
