package tri.novica.gfssystem.entity.raspored;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Raspored jednog perioda (semestar ili godina; spec najave odsustva, odeljak 2): naziv, datumi važenja, prag najava za
 * obaveštenje i stavke. Periodi smeju da se preklapaju; stari ostaju kao istorija. Termini se ne čuvaju, računa ih
 * {@code Termini} iz stavki.
 */
@Entity
@Table(name = "rasporedi")
@Getter @Setter @NoArgsConstructor
public class Raspored {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String naziv;
    @Column(nullable = false)
    private LocalDate vaziOd;
    @Column(nullable = false)
    private LocalDate vaziDo;
    /** Obaveštenje nastavniku kad za jedan termin odsustvo najavi više od ovoliko studenata. */
    @Column(nullable = false)
    private int pragNajava = 5;
    /**
     * Vreme podsetnika dan pre časa (lokalno, kao termini); {@code null} = podsetnik za termine ovog perioda isključen. Nov
     * period dobija 18:00, postojeći su ga dobili migracijom V22.
     */
    @Column(name = "podsetnik_vreme")
    private LocalTime podsetnikVreme = LocalTime.of(18, 0);
    @Column(nullable = false)
    private LocalDateTime kreirano;
    /** Stavke po id-ju; brisanje perioda briše stavke (i u bazi, FK CASCADE). */
    @OneToMany(mappedBy = "raspored", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<RasporedStavka> stavke = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Raspored that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Raspored.class.hashCode();
    }
}
