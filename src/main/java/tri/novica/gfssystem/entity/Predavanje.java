package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
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
    private Integer posecenost = 0;

    @OneToMany(mappedBy = "predavanje", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<Domaci> domaci = new HashSet<>();
    @OneToMany(mappedBy = "predavanje", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE, CascadeType.DETACH})
    private Set<Aktivnost> aktivnosti = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Predavanje that = (Predavanje) o;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(predmet, that.predmet) && Objects.equals(grupa, that.grupa) && rb == that.rb;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public void umanjiPosecenost() {
//        if (posecenost == 0) throw new SystemException("POSECENOST NE MOZE BITI NEGATIVNA! ID = " + id, HttpStatus.BAD_REQUEST);
        if (posecenost <= 0) {
            posecenost=0;
            return;
        }
        posecenost--;
    }

    public void uvecajPOsecenost(){
        posecenost++;
    }

    public void izracunajPOsecenost(){
        posecenost = aktivnosti == null ? 0 : aktivnosti.size();
    }
}
