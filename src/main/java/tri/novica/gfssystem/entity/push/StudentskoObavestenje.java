package tri.novica.gfssystem.entity.push;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Jedno obaveštenje studentu (spec aplikacije 6.8): red po događaju i studentu, sadržaj (naslov, tekst, putanja, oznaka)
 * zamrznut u trenutku događaja i isti kao payload push poruke. {@code kljuc} je jedinstven po studentu, pa isti događaj
 * nikad ne pravi dva reda (ni dva push-a). Nastaje bez obzira na prekidače; prekidači filtriraju samo prikaz i slanje.
 * Nikad ne nosi ocenu, ime drugog studenta, razlog odsustva ni ništa što je student ukucao.
 */
@Entity
@Table(name = "studentska_obavestenja")
@Getter @Setter @NoArgsConstructor
public class StudentskoObavestenje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private VrstaStudentskogObavestenja vrsta;
    @Column(nullable = false, length = 120)
    private String kljuc;
    @Column(nullable = false, length = 120)
    private String naslov;
    @Column(nullable = false, length = 400)
    private String tekst;
    @Column(nullable = false, length = 120)
    private String putanja;
    @Column(nullable = false, length = 80)
    private String oznaka;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof StudentskoObavestenje that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return StudentskoObavestenje.class.hashCode();
    }
}
