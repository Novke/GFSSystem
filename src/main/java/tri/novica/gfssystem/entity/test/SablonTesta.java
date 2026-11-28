package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.TipTesta;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Šablon testa (spec 7): recept po predmetu i opciono tipu testa. Test pri pravljenju prepisuje stavke
 * ({@link TestStavka}), pa izmena šablona ne menja napravljene testove.
 */
@Entity
@Table(name = "sabloni_testa")
@Getter @Setter @NoArgsConstructor
public class SablonTesta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Predmet predmet;
    @ManyToOne
    private TipTesta tipTesta;
    @Column(nullable = false, length = 120)
    private String naziv;
    @Column(columnDefinition = "text")
    private String opis;
    @Column(nullable = false)
    private int trajanjeMin;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private RezimTesta rezim;
    @Column(nullable = false)
    private int brojGrupa;
    private Integer pragProlaza;
    @Column(nullable = false)
    private boolean arhiviran;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;
    @OneToMany(mappedBy = "sablon", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rb")
    private List<SablonStavka> stavke = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof SablonTesta that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return SablonTesta.class.hashCode();
    }
}
