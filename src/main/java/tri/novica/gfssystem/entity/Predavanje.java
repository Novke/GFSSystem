package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@Table(name = "predavanja")
public class Predavanje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @NonNull
    private Predmet predmet;
    @ManyToOne
    private Grupa grupa;

    @Column(unique = true)
    private int rb;
    private LocalDate datum;
    private String tema;
    private Integer posecenost;

    @OneToMany(mappedBy = "predavanje", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<Domaci> domaci;
}
