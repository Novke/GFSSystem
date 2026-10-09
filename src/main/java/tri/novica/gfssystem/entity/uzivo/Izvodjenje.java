package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;

import java.time.LocalDateTime;

/**
 * Jedno izvođenje prezentacije uživo. {@code kod} je kod za pridruživanje (ostaje i posle kraja), {@code aktivanKod}
 * je isti kod dok je izvođenje AKTIVNO, inače {@code null} (jedinstven samo među aktivnim). Ostala polja su trenutno
 * stanje (slajd, korak, faza pitanja, ekran); {@code verzija} raste za 1 po komandi.
 */
@Entity
@Table(name = "izvodjenja")
@Getter @Setter @NoArgsConstructor
@DynamicUpdate
public class Izvodjenje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Prezentacija prezentacija;
    @Column(nullable = false, columnDefinition = "char(6)")
    private String kod;
    @Column(columnDefinition = "char(6)")
    private String aktivanKod;
    @ManyToOne(fetch = FetchType.LAZY)
    private Grupa grupa;
    @ManyToOne(fetch = FetchType.LAZY)
    private Predavanje predavanje;
    @Column(nullable = false)
    private boolean cuvanje;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private StatusIzvodjenja status;
    @Column(nullable = false)
    private LocalDateTime pocetak;
    private LocalDateTime kraj;
    private Long trenutniSlajdId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private Prikaz prikaz;
    @Column(nullable = false)
    private int korak;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 20)
    private Faza faza;
    private Long trenutnaRundaId;
    @Column(nullable = false) private boolean rezultatiPrikazani;
    @Column(nullable = false) private boolean tacanPrikazan;
    @Column(nullable = false) private boolean rangListaPrikazana;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private Ekran ekran;
    @Column(nullable = false) private boolean qrPrikazan;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private TelefonPrikaz telefonPrikaz;
    @Column(nullable = false) private boolean detaljiDozvoljeni;
    @Column(nullable = false) private boolean takmicenje;
    @Column(nullable = false) private long verzija;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Izvodjenje that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Izvodjenje.class.hashCode();
    }
}
