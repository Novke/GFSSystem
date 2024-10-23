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
@Table(name = "domaci")
public class Domaci {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(optional = false)
    Predmet predmet;

    private String naslov;
    @Column(length = 3000)
    private String text;
    private LocalDate datum;
    private Boolean pregledan = false;

    @OneToMany(mappedBy = "domaci", cascade = CascadeType.ALL)
    private Set<UradjenDomaci> uradjeniDomaci;

    @ManyToOne
    private Predavanje predavanje;

    @ManyToOne
    private Grupa grupa;
}
