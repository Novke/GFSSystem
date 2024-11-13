package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tri.novica.gfssystem.entity.converter.TestGrupaConverter;

import java.time.LocalDate;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "testovi")
public class Test {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private TipTesta tipTesta;

    @ManyToOne(optional = false)
    private Predmet predmet;

    @ManyToOne(optional = false)
    private Grupa grupa;

    private LocalDate datum;
    private Integer maxPoena;
    private Boolean pregledan;

    @OneToMany
    private Set<Polaganje> polaganja;
    @Convert(converter = TestGrupaConverter.class)
    private Set<TestGrupa> grupe;

}
