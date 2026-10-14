package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Otpremljena slika (slajd ili pitanje). Bajtovi su fajl {@code <gfs.mediji.dir>/<id>}, ovde su samo metapodaci. */
@Entity
@Table(name = "mediji")
@Getter @Setter @NoArgsConstructor
public class Medij {
    /** UUID koji dodeljuje {@code MedijService}, bez generatora. */
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false)
    private String naziv;
    @Column(nullable = false, length = 100)
    private String mime;
    @Column(nullable = false)
    private long velicina;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Medij that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Medij.class.hashCode();
    }
}
