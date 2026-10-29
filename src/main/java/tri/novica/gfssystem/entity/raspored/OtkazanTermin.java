package tri.novica.gfssystem.entity.raspored;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Termin (stavka, datum) koji nastavnik ne drži; jedan red po terminu, {@code napomena} je nastavnikov tekst za studente. */
@Entity
@Table(name = "otkazani_termini")
@Getter @Setter @NoArgsConstructor
public class OtkazanTermin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private RasporedStavka stavka;
    @Column(nullable = false)
    private LocalDate datum;
    @Column(length = 200)
    private String napomena;
    @Column(nullable = false)
    private LocalDateTime otkazano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof OtkazanTermin that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return OtkazanTermin.class.hashCode();
    }
}
