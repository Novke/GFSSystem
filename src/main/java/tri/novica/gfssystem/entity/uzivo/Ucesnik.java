package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import tri.novica.gfssystem.entity.Student;

import java.time.LocalDateTime;

/**
 * Učesnik izvođenja (telefon studenta). U bazi je samo SHA-256 (hex) tokena iz kolačića, nikad sam token. {@code student}
 * je prijavljen student iza učesnika (bodovan run); null za učesnika koji je samo ukucao ime.
 */
@Entity
@Table(name = "ucesnici")
@Getter @Setter @NoArgsConstructor
@DynamicUpdate
public class Ucesnik {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Izvodjenje izvodjenje;
    @Column(nullable = false, length = 40)
    private String ime;
    @Column(nullable = false, columnDefinition = "char(64)")
    private String tokenHash;
    @Column(nullable = false)
    private boolean izbacen;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    /** Brisanje studenta ostavlja učesnika bez njega (ON DELETE SET NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Student student;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Ucesnik that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Ucesnik.class.hashCode();
    }
}
