package tri.novica.gfssystem.entity.prijava;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Student;

import java.time.LocalDateTime;

/**
 * Prijavljen uređaj studenta (kolačić {@code gfs_student}); nalog nema lozinku ni svoje stanje, uređaji vise direktno o
 * studentu (brisanje studenta ih briše u bazi, CASCADE). U bazi je samo SHA-256 (hex) tokena.
 */
@Entity
@Table(name = "studentski_uredjaji")
@Getter @Setter @NoArgsConstructor
@DynamicUpdate
public class StudentskiUredjaj {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Student student;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 64, unique = true)
    private String tokenHes;
    /** Opis uređaja iz {@code User-Agent} (npr. "Android · Chrome"), bez verzija. */
    @Column(length = 120)
    private String opis;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private NacinPrijave nacin;
    @Column(nullable = false)
    private LocalDateTime prviUlaz;
    @Column(nullable = false)
    private LocalDateTime poslednjiUlaz;
    @Column(nullable = false)
    private LocalDateTime istice;
    private LocalDateTime opozvan;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof StudentskiUredjaj that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return StudentskiUredjaj.class.hashCode();
    }
}
