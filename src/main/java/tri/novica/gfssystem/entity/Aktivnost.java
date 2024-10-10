package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

@Entity
@Data
@NoArgsConstructor
@Table(name = "aktivnosti")
public class Aktivnost {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @NonNull
    private Student student;

    @ManyToOne(optional = false)
    @NonNull
    private Predavanje predavanje;
    @Enumerated(EnumType.STRING)
    private TipAktivnosti tip;
    private String napomene;

    public int getBodovi(){
        return tip == null ? 0 : tip.getVrednost();
    }

}
