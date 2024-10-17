package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@Table(name = "grupe")
public class Grupa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String naziv;
    private Integer godinaUpisa;

    @OneToMany(cascade = CascadeType.PERSIST, mappedBy = "grupa")
    private List<Student> studenti;
    @OneToMany(mappedBy = "grupa")
    private List<Predavanje> predavanja;
    @OneToMany(mappedBy = "grupa")
    private List<Domaci> domaci;

}
