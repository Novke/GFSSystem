package tri.novica.gfssystem.entity.anketa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.converter.StringListJsonConverter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sačuvano pitanje ankete ({@code ankete_sabloni}): tekst, odgovori i podrazumevana publika, plus izgled prečice
 * ({@code precica}, {@code precicaRb}, ikonica, boja, kratak naziv, "dodir pokreće odmah"). {@code folderId} {@code null} =
 * bez foldera. Pokretanje ankete iz šablona samo uveća {@code brojPokretanja}; šablon se ne menja.
 */
@Entity
@Table(name = "ankete_sabloni")
@Getter @Setter @NoArgsConstructor
public class AnketaSablon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "folder_id")
    private Long folderId;
    @Column(nullable = false, length = 300)
    private String pitanje;
    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> opcije;
    @Column(name = "vise_odgovora", nullable = false)
    private boolean viseOdgovora;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PublikaAnkete publika = PublikaAnkete.NA_CASU;
    @Column(name = "trajanje_sekunde")
    private Integer trajanjeSekunde;
    @Column(name = "rok_sati")
    private Integer rokSati;
    @Column(nullable = false)
    private boolean precica;
    @Column(name = "precica_rb")
    private Integer precicaRb;
    @Column(length = 16)
    private String ikonica;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private BojaPrecice boja;
    @Column(name = "kratak_naziv", length = 30)
    private String kratakNaziv;
    @Column(name = "pokreni_odmah", nullable = false)
    private boolean pokreniOdmah;
    @Column(nullable = false)
    private int rb;
    @Column(name = "broj_pokretanja", nullable = false)
    private int brojPokretanja;
    @Column(name = "poslednje_pokretanje")
    private LocalDateTime poslednjePokretanje;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;
}
