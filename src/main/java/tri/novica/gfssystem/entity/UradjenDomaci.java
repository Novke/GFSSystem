package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

@Entity
@Data
@NoArgsConstructor
@Table(name = "uradjeni_domaci")
public class UradjenDomaci {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(optional = false)
    private Student student;

    @NonNull
    @ManyToOne(optional = false)
    private Domaci domaci;

    //max = 10
    private int bodovi;
    private String napomene;
    private boolean prepisivanje = false;
}
