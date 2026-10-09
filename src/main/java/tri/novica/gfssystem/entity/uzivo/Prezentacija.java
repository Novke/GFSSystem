package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Predmet;

import java.time.LocalDateTime;

/** Prezentacija predmeta. Slajdovi nisu kolekcija ovde, čitaju se preko {@code SlajdRepository} po {@code rb}. */
@Entity
@Table(name = "prezentacije")
@Getter @Setter @NoArgsConstructor
public class Prezentacija {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Predmet predmet;
    @Column(nullable = false, length = 200)
    private String naziv;
    @Column(length = 1000)
    private String opis;
    @Column(nullable = false)
    private boolean takmicenje;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private TelefonPrikaz telefonPrikaz;
    @Column(nullable = false)
    private boolean detaljiDozvoljeni;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Prezentacija that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Prezentacija.class.hashCode();
    }
}
