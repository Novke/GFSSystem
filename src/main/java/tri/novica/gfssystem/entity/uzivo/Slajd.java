package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;

/**
 * Slajd prezentacije: INFO (naslov, Markdown sadržaj, slika), PITANJE (poseduje {@link Pitanje}), PLATNO ili ZADATAK
 * (čvor banke {@code grupaZadataka}, tajmer i broj različitih varijanti).
 */
@Entity
@Table(name = "slajdovi")
@Getter @Setter @NoArgsConstructor
public class Slajd {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Prezentacija prezentacija;
    @Column(nullable = false)
    private int rb;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private TipSlajda tip;
    @Column(length = 300)
    private String naslov;
    @Column(columnDefinition = "mediumtext")
    private String sadrzaj;
    @ManyToOne
    private Medij slika;
    @Column(columnDefinition = "text")
    private String beleske;
    /** Stavke liste u sadržaju se otkrivaju jedna po jedna. */
    @Column(nullable = false)
    private boolean postepeno;
    /** JSON dokument platna (spec 4.3), samo za PLATNO; čita/piše {@code PlatnoJson}. */
    @Column(columnDefinition = "mediumtext")
    private String platno;
    /** Prelaz pri ulasku na slajd (faza 5); sve kolone null = bez prelaza (Hibernate tada vraća null). */
    @Embedded
    private PrelazSlajda prelaz;
    /** Klodova serija upisa (MCP); null za slajdove napravljene u UI-ju ili uvozom. */
    @Column(length = 60)
    private String serija;
    /** Čvor banke (list ili unutrašnji) ZADATAK slajda; brisanje grupe ostavlja slajd bez nje (ON DELETE SET NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupa_zadataka_id")
    private GrupaZadataka grupaZadataka;
    /** Tajmer ZADATAK slajda u sekundama; null = bez tajmera. */
    private Integer zadatakVremeSekunde;
    /** Broj različitih varijanti koje se izvlače pri otvaranju; null = sve aktivne. */
    private Integer zadatakBrojVarijanti;
    /** Nivo varijanti (1-3) koje se izvlače pri otvaranju; null = svi nivoi. Kolona je tinyint. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer zadatakNivo;
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pitanje_id")
    private Pitanje pitanje;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Slajd that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Slajd.class.hashCode();
    }
}
