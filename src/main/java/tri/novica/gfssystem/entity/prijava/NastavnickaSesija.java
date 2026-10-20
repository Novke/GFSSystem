package tri.novica.gfssystem.entity.prijava;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Sesija nastavnika na jednom uređaju (kolačić {@code gfs_nastavnik}). U bazi je samo SHA-256 (hex) tokena, nikad
 * sam token. {@code istice} se pomera korišćenjem (90 dana od poslednjeg ulaza); {@code opozvana} != null = odjavljena.
 */
@Entity
@Table(name = "nastavnicke_sesije")
@Getter @Setter @NoArgsConstructor
@DynamicUpdate
public class NastavnickaSesija {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 64, unique = true)
    private String tokenHes;
    /** Opis uređaja iz {@code User-Agent} (npr. "Android · Chrome"), bez verzija. */
    @Column(length = 120)
    private String opis;
    @Column(length = 64)
    private String ip;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime poslednjiUlaz;
    @Column(nullable = false)
    private LocalDateTime istice;
    private LocalDateTime opozvana;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof NastavnickaSesija that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return NastavnickaSesija.class.hashCode();
    }
}
