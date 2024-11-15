package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "tipovi_testa")
public class TipTesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "tipTesta")
    private Set<Test> testovi;
    private String naziv;
    private Boolean aktivan = true;
    @ManyToOne(optional = false)
    private Predmet predmet;

    public TipTesta(String naziv, Predmet predmet) {
        this.naziv = naziv;
        this.predmet = predmet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TipTesta tipTesta = (TipTesta) o;
        if (id != null && tipTesta.id != null) return Objects.equals(id, tipTesta.id);
        return Objects.equals(naziv, tipTesta.naziv) && Objects.equals(aktivan, tipTesta.aktivan) && Objects.equals(predmet, tipTesta.predmet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(naziv, aktivan, predmet);
    }
}
