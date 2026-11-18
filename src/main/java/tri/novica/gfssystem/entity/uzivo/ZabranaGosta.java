package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Zabrana ulaska telefona izbačenog učesnika (tabela {@code zabrane_gostiju}, V25): SHA-256 tokena iz kolačića
 * {@code gfs_uzivo}, za izvođenje ili (sa {@code predavanjeId}) za sva izvođenja tog predavanja. Preživljava "Ne čuvaj";
 * nova prijava sa zabranjenim tokenom prenosi zabranu na nov token. Briše se posle 2 dana ({@code CiscenjePrijave}).
 */
@Entity
@Table(name = "zabrane_gostiju")
@Getter @Setter @NoArgsConstructor
public class ZabranaGosta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, columnDefinition = "char(64)")
    private String tokenHash;
    @Column(name = "izvodjenje_id", nullable = false)
    private Long izvodjenjeId;
    @Column(name = "predavanje_id")
    private Long predavanjeId;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ZabranaGosta that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return ZabranaGosta.class.hashCode();
    }
}
