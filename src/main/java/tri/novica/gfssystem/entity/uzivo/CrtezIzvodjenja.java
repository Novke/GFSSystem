package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Crtež jedne strane izvođenja (tabela {@code crtezi_izvodjenja}, V28): strana je (slajd, pozadina). Veze su goli id-jevi:
 * FK {@code CASCADE} na izvođenje (brisanje izvođenja briše crteže, "Ne čuvaj" ne), {@code SET NULL} na slajd (crtež ostaje
 * u pregledu kao "obrisan slajd"). {@code elementi} je JSON niz elemenata ({@link tri.novica.gfssystem.service.uzivo.CrtanjeJson}).
 */
@Entity
@Table(name = "crtezi_izvodjenja")
@Getter @Setter @NoArgsConstructor
public class CrtezIzvodjenja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "izvodjenje_id", nullable = false)
    private Long izvodjenjeId;
    @Column(name = "slajd_id")
    private Long slajdId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private PozadinaCrteza pozadina;
    @Column(nullable = false, columnDefinition = "mediumtext")
    private String elementi;
    @Column(nullable = false)
    private int verzija;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof CrtezIzvodjenja that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return CrtezIzvodjenja.class.hashCode();
    }
}
