package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.converter.StringListJsonConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Pitanje sa slajda (slajd ga poseduje 1:1). Polja koja ne važe za {@link #tip} su {@code null}: opcije za
 * JEDAN_TACAN/VISE_TACNIH/ANKETA/TACNO_NETACNO, broj* za BROJ, tekstPrikaz i prihvatljiviOdgovori za KRATAK_TEKST,
 * skala* za SKALA.
 */
@Entity
@Table(name = "pitanja")
@Getter @Setter @NoArgsConstructor
public class Pitanje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private TipPitanja tip;
    @Column(columnDefinition = "text", nullable = false)
    private String tekst;
    @ManyToOne
    private Medij slika;
    private Integer vremeSekunde;
    private Double brojTacno;
    private Double brojOdstupanje;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 20)
    private OdstupanjeTip odstupanjeTip;
    @Column(length = 30)
    private String jedinica;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 20)
    private TekstPrikaz tekstPrikaz;
    /** Tačni odgovori za KRATAK_TEKST (JSON niz u koloni). */
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "text")
    private List<String> prihvatljiviOdgovori;
    @Column(length = 60)
    private String skalaMinOznaka;
    @Column(length = 60)
    private String skalaMaxOznaka;

    @OneToMany(mappedBy = "pitanje", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rb")
    private List<PitanjeOpcija> opcije = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Pitanje that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Pitanje.class.hashCode();
    }
}
