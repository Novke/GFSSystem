package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Student;

import java.time.LocalDateTime;

/**
 * Oznaka ponašanja na kartonu studenta (tabela {@code oznake_ponasanja}, V25): sakriven odgovor, utišan, izbačen. Piše je
 * moderacija u konzoli samo za prijavljenog studenta (gost nema karton). Veze ka predmetu, predavanju, izvođenju i rundi su
 * goli id-jevi sa FK {@code ON DELETE SET NULL}: oznaka preživljava "Ne čuvaj" i brisanje izvođenja. Oznaka
 * {@link VrstaOznake#IZBACEN_PREDAVANJE} je i zabrana ulaska u izvođenja tog predavanja; nastavnik je briše na kartonu.
 */
@Entity
@Table(name = "oznake_ponasanja")
@Getter @Setter @NoArgsConstructor
public class OznakaPonasanja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private VrstaOznake vrsta;
    /** Sakriveni tekst (samo {@link VrstaOznake#SAKRIVEN_ODGOVOR}). */
    @Column(length = 200)
    private String tekst;
    @Column(name = "predmet_id")
    private Long predmetId;
    @Column(name = "predavanje_id")
    private Long predavanjeId;
    @Column(name = "izvodjenje_id")
    private Long izvodjenjeId;
    @Column(name = "runda_id")
    private Long rundaId;
    /** Naziv prezentacije u trenutku oznake (izvođenje može biti obrisano). */
    @Column(length = 255)
    private String prezentacija;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof OznakaPonasanja that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return OznakaPonasanja.class.hashCode();
    }
}
