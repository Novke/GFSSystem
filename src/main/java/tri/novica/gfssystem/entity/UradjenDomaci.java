package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import java.util.Objects;

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
    private boolean oslobodjen = false;

    public static UradjenDomaci oslobodjenDomaci(Student student, Domaci domaci){
        UradjenDomaci uradjenDomaci = new UradjenDomaci();

        uradjenDomaci.student = student;
        uradjenDomaci.domaci = domaci;
        uradjenDomaci.oslobodjen = true;
        uradjenDomaci.bodovi = 10;

        return uradjenDomaci;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UradjenDomaci that = (UradjenDomaci) o;
        return Objects.equals(student.getId(), that.student.getId()) && Objects.equals(domaci.getId(), that.domaci.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(student.getId(), domaci.getId());
    }
}
