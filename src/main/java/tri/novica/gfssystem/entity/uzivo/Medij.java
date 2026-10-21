package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Otpremljen fajl (slika slajda ili pitanja, prilog i render banke zadataka). Bajtovi su fajl {@code <gfs.mediji.dir>/<id>}, ovde su samo metapodaci. */
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
    /** Podrazumevano SLIKA, i u bazi ({@code DEFAULT 'SLIKA'}), pa stari redovi i stari upload rade nepromenjeno. */
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private VrstaMedija vrsta = VrstaMedija.SLIKA;
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
