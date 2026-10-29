package tri.novica.gfssystem.entity.raspored;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tri.novica.gfssystem.entity.Student;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Student je javio da neće doći na termin (stavka, datum). {@code razlog} je ono što je student ukucao (do 200 znakova):
 * vidi ga samo nastavnik u aplikaciji, nikad ne ide u mejl. Ne utiče na prisustvo ni ocenu.
 */
@Entity
@Table(name = "najave_odsustva")
@Getter @Setter @NoArgsConstructor
public class NajavaOdsustva {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private RasporedStavka stavka;
    @Column(nullable = false)
    private LocalDate datum;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Student student;
    @Column(length = 200)
    private String razlog;
    @Column(nullable = false)
    private LocalDateTime vreme;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof NajavaOdsustva that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return NajavaOdsustva.class.hashCode();
    }
}
