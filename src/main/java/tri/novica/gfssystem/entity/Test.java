package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tri.novica.gfssystem.entity.converter.TestGrupaConverter;

import java.time.LocalDate;
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
    private Boolean pregledan;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "test")
    private Set<Polaganje> polaganja = new HashSet<>();
    @Convert(converter = TestGrupaConverter.class)
    private Set<TestGrupa> grupe = new HashSet<>();

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
