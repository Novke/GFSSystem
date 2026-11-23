package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bela tabla predavanja (tabela {@code table_predavanja}, V28): jedna po predavanju, dugme "Tabla" bez prezentacije. FK
 * {@code CASCADE} na predavanje. {@code elementi} je JSON niz elemenata kao kod {@link CrtezIzvodjenja}.
 */
@Entity
@Table(name = "table_predavanja")
@Getter @Setter @NoArgsConstructor
public class TablaPredavanja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "predavanje_id", nullable = false, unique = true)
    private Long predavanjeId;
    @Column(nullable = false, columnDefinition = "mediumtext")
    private String elementi;
    @Column(nullable = false)
    private int verzija;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TablaPredavanja that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TablaPredavanja.class.hashCode();
    }
}
