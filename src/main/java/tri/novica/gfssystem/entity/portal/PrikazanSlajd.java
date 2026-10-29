package tri.novica.gfssystem.entity.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Slajd koji je konzola prikazala izvođenju sa grupom (spec studentskog dela 6.1): jedan red po (grupa, slajd), iz prvog
 * izvođenja. Studentu je vidljiv tek kad je to izvođenje završeno ili obrisano ({@code izvodjenjeId} postaje {@code null}).
 * Upis ide samo kroz {@code PrikazaniSlajdoviRepository.upisi} ("ako ne postoji").
 */
@Entity
@Table(name = "prikazani_slajdovi")
@Getter @Setter @NoArgsConstructor
public class PrikazanSlajd {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "grupa_id", nullable = false)
    private Long grupaId;
    @Column(name = "slajd_id", nullable = false)
    private Long slajdId;
    @Column(name = "izvodjenje_id")
    private Long izvodjenjeId;
    @Column(nullable = false)
    private LocalDateTime prviPut;
}
