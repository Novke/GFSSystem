package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.converter.TestGrupaConverter;
import tri.novica.gfssystem.entity.test.NacinTesta;
import tri.novica.gfssystem.entity.test.RezimTesta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "testovi")
public class Test {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    private TipTesta tipTesta;

    @ManyToOne(optional = false)
    private Predmet predmet;

    @ManyToOne(optional = false)
    private Grupa grupa;

    private LocalDate datum;
    private Integer maxPoena;
    /** Opcioni prag prolaza u poenima (kolona {@code prag_prolaza}); null = test nema pojam prolaza, vidi {@code Prolaz}. */
    private Integer pragProlaza;
    private Boolean pregledan;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "test")
    private Set<Polaganje> polaganja = new HashSet<>();
    @Convert(converter = TestGrupaConverter.class)
    private Set<TestGrupa> grupe = new HashSet<>();

    /** Na papiru (podrazumevano, i za redove stare slike) ili online (spec online testova; polja ispod važe samo za ONLINE). */
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private NacinTesta nacin = NacinTesta.PAPIR;
    /** Šablon iz kog je test napravljen (FK SET NULL: brisanje šablona ostavlja prepis stavki na testu). */
    @Column(name = "sablon_id")
    private Long sablonId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 20)
    private RezimTesta rezim;
    private Integer trajanjeMin;
    /** Seme izvlačenja (isti ulaz i seme daju isti izbor). */
    private Long seme;
    /** Trenutak objave rezultata studentima ({@code null} = nije objavljeno); radi i za test na papiru. */
    private LocalDateTime objavljeno;
    /** Uz objavu: student vidi i tačne vrednosti i sliku rešenja. */
    @Column(nullable = false)
    private boolean objavaTacni;

    public boolean isOnline() {
        return nacin == NacinTesta.ONLINE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Test test = (Test) o;
        if (id != null && test.id != null) return Objects.equals(id, test.id);
        return Objects.equals(tipTesta, test.tipTesta) && Objects.equals(predmet, test.predmet) && Objects.equals(grupa, test.grupa) && Objects.equals(datum, test.datum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipTesta, predmet, grupa, datum);
    }

    public void generisiGrupe(Integer brojGrupa) {
        grupe = new LinkedHashSet<>();

        Arrays.stream(TestGrupa.values()).forEach( tg -> {
            if (grupe.size()<brojGrupa) grupe.add(tg);
        });
    }
}
