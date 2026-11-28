package tri.novica.gfssystem.entity.prijava;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Zahtev za prijavu računara QR-om (spec online testova 11): token iz QR-a i tajna iz kolačića {@code gfs_racunar}, u
 * bazi samo SHA-256 (hex); važi 2 minuta, jednokratan. {@code studentId}/{@code odobreno} postavlja telefon prijavljen
 * kodom, {@code preuzeto} računar kad dobije sesiju.
 */
@Entity
@Table(name = "prijave_racunara")
@Getter @Setter @NoArgsConstructor
public class PrijavaRacunara {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 64, unique = true)
    private String tokenHes;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 64)
    private String tajnaHes;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 4)
    private String broj;
    @Column(length = 64)
    private String ip;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime istice;
    @Column(name = "student_id")
    private Long studentId;
    private LocalDateTime odobreno;
    private LocalDateTime preuzeto;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PrijavaRacunara that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return PrijavaRacunara.class.hashCode();
    }
}
