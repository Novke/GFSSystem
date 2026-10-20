package tri.novica.gfssystem.entity.prijava;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Jedan pokušaj za limit (brojač u bazi, preživljava restart); stariji od 2 dana se brišu. */
@Entity
@Table(name = "pokusaji")
@Getter @Setter @NoArgsConstructor
public class Pokusaj {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private VrstaPokusaja vrsta;
    @Column(nullable = false, length = 128)
    private String kljuc;
    @Column(nullable = false)
    private LocalDateTime vreme;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Pokusaj that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Pokusaj.class.hashCode();
    }
}
