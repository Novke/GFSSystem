package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

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
    private Boolean polozio; //ako ne moze da se padne, default je true
    private Boolean prepisivao = false;
    private String napomene;

    public static Polaganje defaultPolaganje(Test test, Student student) {
        Polaganje polaganje = new Polaganje();
        polaganje.test = test;
        polaganje.student = student;
        polaganje.prepisivao = false;
        return polaganje;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Polaganje polaganje = (Polaganje) o;
        if (id!=null && polaganje.id != null) return id.equals(polaganje.id);
        return Objects.equals(student.getId(), polaganje.student.getId()) && Objects.equals(test.getId(), polaganje.test.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(student, test);
    }
}
