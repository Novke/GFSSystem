package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@Table(name = "domaci")
public class Domaci {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(optional = false)
    Predmet predmet;

    @Column(length = 3000)
    private String text;
    private LocalDate datum;

    @OneToMany(mappedBy = "domaci")
    private List<UradjenDomaci> uradjeniDomaci;
}
