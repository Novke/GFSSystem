package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@Table(name = "predmeti")
public class Predmet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String naziv;

    @OneToMany(mappedBy = "predmet")
    private List<Predavanje> predavanja;
    @OneToMany(mappedBy = "predmet")
    private List<Domaci> domaci;

}
