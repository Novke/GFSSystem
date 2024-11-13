package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "polaganja")
public class Polaganje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Student student;

    @ManyToOne(optional = false)
    private Test test;

    @Enumerated
    private TestGrupa grupa;

    private Integer ostvareniPoeni;
    private Boolean polozio;
    private Boolean prepisivao = false;



}
