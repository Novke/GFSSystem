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
 * Grupa zadataka u banci predmeta: koren ili podgrupa ({@code roditelj}), posebno stablo po {@link Namena}. Varijante
 * se ne drže kao kolekcija ovde, čitaju se preko {@code VarijantaRepository}.
 */
@Entity
@Table(name = "grupe_zadataka")
@Getter @Setter @NoArgsConstructor
public class GrupaZadataka {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Predmet predmet;
    /** null = koren stabla. */
    @ManyToOne
    private GrupaZadataka roditelj;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private Namena namena;
    @Column(nullable = false, length = 120)
    private String naziv;
    @Column(columnDefinition = "text")
    private String opis;
    /** Redni broj među grupama istog roditelja. */
    @Column(nullable = false)
    private int rb;
    /** Težina 1-5, null za unutrašnje čvorove stabla; kolona je tinyint. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer tezina;
    @Column(columnDefinition = "text")
    private String uputstvo;
    /** Šema odgovora: JSON lista polja (prazna = rad na papiru); običan tekst u bazi, čita/piše ga servis. */
    @Column(columnDefinition = "text", nullable = false)
    private String polja = "[]";
    /** Ko je napravio grupu: nastavnik u UI-ju (podrazumevano, i za redove stare slike) ili Klod kroz MCP. */
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private IzvorVarijante izvor = IzvorVarijante.RUCNO;
    /** Klodova serija upisa (obrazac {@code ^[a-z0-9-]{3,60}$}); null za ručno napravljene grupe. */
    @Column(length = 60)
    private String serija;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof GrupaZadataka that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return GrupaZadataka.class.hashCode();
    }
}
