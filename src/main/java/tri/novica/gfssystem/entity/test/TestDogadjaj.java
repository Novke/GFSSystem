package tri.novica.gfssystem.entity.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Meki znak ili događaj primerka (spec 13.3): samo prikaz nastavniku, ništa ne blokira. Vrste u {@code DogadjajiTesta}. */
@Entity
@Table(name = "test_dogadjaji")
@Getter @Setter @NoArgsConstructor
public class TestDogadjaj {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "primerak_id", nullable = false)
    private Long primerakId;
    @Column(nullable = false)
    private LocalDateTime vreme;
    @Column(nullable = false, length = 30)
    private String vrsta;
    @Column(length = 200)
    private String detalj;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TestDogadjaj that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TestDogadjaj.class.hashCode();
    }
}
