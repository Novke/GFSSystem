package tri.novica.gfssystem.entity.cas;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Student;

import java.time.LocalDateTime;

/**
 * Čekiranje studenta QR-om na predavanju (spec studentskog dela 4.9): jedan red po (predavanje, student), {@code vreme} je
 * trenutak prvog skeniranja i ne menja se. {@code uredjajHash}: SHA-256 kolačića {@code gfs_uredjaj} (oznaka telefona),
 * {@code null} kad ga nije bilo. Brisanje predavanja ili studenta briše red (FK {@code ON DELETE CASCADE}); nema veza nazad
 * iz {@code Predavanje}/{@code Student}.
 */
@Entity
@Table(name = "cekiranja")
@Getter @Setter @NoArgsConstructor
public class Cekiranje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Predavanje predavanje;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Student student;
    @Column(nullable = false)
    private LocalDateTime vreme;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64)
    private String uredjajHash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Cekiranje that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Cekiranje.class.hashCode();
    }
}
