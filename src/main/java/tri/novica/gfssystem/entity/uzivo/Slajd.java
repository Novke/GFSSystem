package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Slajd prezentacije: INFO (naslov, Markdown sadržaj, slika) ili PITANJE (poseduje {@link Pitanje}). */
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
