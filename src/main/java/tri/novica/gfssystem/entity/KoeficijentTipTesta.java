package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "koeficijenti_tip_testa",
        uniqueConstraints = @UniqueConstraint(columnNames = {"koeficijenti_id", "tip_testa_id"}))
public class KoeficijentTipTesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "koeficijenti_id")
    private KoeficijentiOcenjivanja koeficijenti;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tip_testa_id")
    private TipTesta tipTesta;

    private Double maxPoena;           // koliko max poena nosi ovaj tip testa (za normalizaciju)

    public KoeficijentTipTesta(KoeficijentiOcenjivanja koeficijenti, TipTesta tipTesta) {
        this.koeficijenti = koeficijenti;
        this.tipTesta = tipTesta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KoeficijentTipTesta that = (KoeficijentTipTesta) o;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(koeficijenti.getId(), that.koeficijenti.getId())
                && Objects.equals(tipTesta.getId(), that.tipTesta.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(koeficijenti != null ? koeficijenti.getId() : null,
                tipTesta != null ? tipTesta.getId() : null);
    }
}
