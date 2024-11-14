package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "studenti", uniqueConstraints = @UniqueConstraint(columnNames = {"index", "godina"}))
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ime;
    private String prezime;
    private int godina;
    private String indeks;
    private String brojTelefona;
    @ManyToOne
    private Grupa grupa;
    @OneToMany(mappedBy = "student")
    private Set<UradjenDomaci> uradjeniDomaci;
    @OneToMany(mappedBy = "student")
    private Set<Aktivnost> aktivnosti;
    @OneToMany(mappedBy = "student")
    private Set<Polaganje> polaganja;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Student student = (Student) o;
        if (id != null && student.id != null) return id.equals(student.id);
        return Objects.equals(indeks, student.indeks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indeks);
    }
}
