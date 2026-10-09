package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * Jedno otvaranje pitanja u izvođenju. {@code snimak} je JSON kopija pitanja u trenutku otvaranja, pa istorija ostaje
 * čitljiva i kad se slajd izmeni ili obriše ({@code slajdId} tada postaje {@code null}). {@code @DynamicUpdate}: izmena
 * runde (zatvaranje, tajmer) ne prepisuje {@code slajd_id}, pa ne proverava FK prema slajdu koji se upravo briše.
 */
@Entity
@Table(name = "pitanje_runde")
@Getter @Setter @NoArgsConstructor
@DynamicUpdate
public class PitanjeRunda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Izvodjenje izvodjenje;
    private Long slajdId;
    @Column(nullable = false)
    private int redniBroj;
    @Column(nullable = false, columnDefinition = "mediumtext")
    private String snimak;
    @Column(nullable = false)
    private LocalDateTime otvoreno;
    private LocalDateTime zatvoreno;
    private LocalDateTime rok;
    /** Preostalo vreme dok je tajmer pauziran. */
    private Long preostaloMs;
    /** Ukupno trajanje (za poene po brzini). */
    private Long trajanjeMs;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PitanjeRunda that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return PitanjeRunda.class.hashCode();
    }
}
