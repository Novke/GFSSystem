package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Zadatak u primerku: snimak varijante (JSON {@code SnimakZadatkaTesta}; ocena zavisi samo od njega i ispravki testa),
 * odgovori studenta (JSON {@code kljuc -> vrednost}) sa brojem izmene {@code rev}, ocena po polju (JSON), automatski i
 * ručni poeni (ručni pobeđuju). {@code varijantaId} je FK RESTRICT.
 */
@Entity
@Table(name = "test_primerak_zadaci")
@Getter @Setter @NoArgsConstructor
public class TestPrimerakZadatak {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "primerak_id", nullable = false)
    private Long primerakId;
    @Column(nullable = false)
    private int rb;
    @Column(name = "stavka_id")
    private Long stavkaId;
    @Column(nullable = false)
    private int mesto;
    @Column(name = "varijanta_id", nullable = false)
    private Long varijantaId;
    @Column(columnDefinition = "mediumtext", nullable = false)
    private String snimak;
    @Column(nullable = false)
    private double maxPoena;
    @Column(columnDefinition = "text")
    private String odgovori;
    @Column(nullable = false)
    private int rev;
    private LocalDateTime sacuvano;
    @Column(columnDefinition = "text")
    private String ocena;
    private Double poeni;
    private Double rucniPoeni;
    @Column(length = 500)
    private String napomena;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TestPrimerakZadatak that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TestPrimerakZadatak.class.hashCode();
    }
}
