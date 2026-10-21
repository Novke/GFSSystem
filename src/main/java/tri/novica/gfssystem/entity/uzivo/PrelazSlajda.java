package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Prelaz pri ulasku na slajd (faza 5, Flyway V10): kolone {@code slajdovi.prelaz_efekat}, {@code prelaz_smer},
 * {@code prelaz_ms}. Upisuje se samo normalizovan ({@code SlajdPP.normalizuj(Prelaz)}); sve kolone null = bez prelaza.
 */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PrelazSlajda {
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "prelaz_efekat", length = 20)
    private TipPrelaza efekat;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "prelaz_smer", length = 20)
    private SmerPrelaza smer;
    @Column(name = "prelaz_ms")
    private Integer trajanjeMs;
}
