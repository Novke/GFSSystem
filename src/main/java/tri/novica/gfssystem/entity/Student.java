package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
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
    private List<UradjenDomaci> uradjeniDomaci;
    @OneToMany(mappedBy = "student")
    private List<Aktivnost> aktivnosti;
}
