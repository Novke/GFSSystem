package tri.novica.gfssystem.entity.prijava;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Student;

import java.time.LocalDateTime;

/**
 * Obaveštenje za nastavnika (opšta tabela). {@code student} je null kad se odnosi na indeks koji ne postoji ili na termin
 * ({@code MNOGO_NAJAVA}); {@code podaci} je JSON string (običan text). Otvoreno = {@code reseno} null, nepročitano =
 * {@code procitano} null. {@code kljuc} (jedinstven, nullable) drži jedno obaveštenje po ključu, npr.
 * {@code MNOGO_NAJAVA:<stavkaId>:<datum>}; stare vrste ga nemaju.
 */
@Entity
@Table(name = "obavestenja")
@Getter @Setter @NoArgsConstructor
public class Obavestenje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 40)
    private VrstaObavestenja vrsta;
    @ManyToOne(fetch = FetchType.LAZY)
    private Student student;
    @Column(columnDefinition = "text", nullable = false)
    private String podaci = "{}";
    @Column(nullable = false)
    private LocalDateTime kreirano;
    private LocalDateTime procitano;
    private LocalDateTime reseno;
    @Column(length = 100, unique = true)
    private String kljuc;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Obavestenje that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Obavestenje.class.hashCode();
    }
}
