package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import java.util.Objects;

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

    public Aktivnost(@NonNull Predavanje predavanje, @NonNull Student student, TipAktivnosti tipAktivnosti) {
        this.predavanje = predavanje;
        this.student = student;
        this.tip = tipAktivnosti;
    }

    public int getBodovi(){
        return tip == null ? 0 : tip.getVrednost();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Aktivnost aktivnost = (Aktivnost) o;
        if (id != null && aktivnost.id != null) return id.equals(aktivnost.id);
        return Objects.equals(student, aktivnost.student) && Objects.equals(predavanje, aktivnost.predavanje)/* && tip == aktivnost.tip*/;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
