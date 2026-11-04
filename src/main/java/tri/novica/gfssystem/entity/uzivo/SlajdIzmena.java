package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Stanje slajda pre jedne Klodove izmene ili brisanja ({@code snimak}: JSON {@code SlajdCmd}, PLATNO sa potezima). Slajd je
 * samo id, bez FK: unos brisanja nadživljava slajd. {@code snimak} je {@code null} kad se vraćanjem slajd obnovio, jer pre
 * toga nije postojao.
 */
@Entity
@Table(name = "slajd_izmene")
@Getter
@Setter
@NoArgsConstructor
public class SlajdIzmena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long prezentacijaId;
    @Column(nullable = false)
    private Long slajdId;
    @Column(nullable = false)
    private int rb;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private VrstaIzmene vrsta;
    @Column(length = 60)
    private String serija;
    @Column(nullable = false, length = 300)
    private String opis;
    private Integer verzijaPre;
    @Column(columnDefinition = "mediumtext")
    private String snimak;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof SlajdIzmena that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return SlajdIzmena.class.hashCode();
    }
}
