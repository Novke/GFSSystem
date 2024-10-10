package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import java.time.LocalDate;

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

    private int rb;
    private LocalDate datum;
    private String tema;
    private Integer posecenost;
}