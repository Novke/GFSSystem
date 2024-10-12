package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

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
    private Integer posecenost;

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
}
