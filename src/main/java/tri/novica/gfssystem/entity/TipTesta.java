package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Entity
@Data
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
}
