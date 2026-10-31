package tri.novica.gfssystem.entity.push;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Web Push pretplata jednog uređaja (spec aplikacije 5.2): pripada <b>tačno jednom</b> od uređaja studenta
 * ({@code studentski_uredjaji}) ili sesije nastavnika ({@code nastavnicke_sesije}), uzetom iz sesije zahteva, nikad iz
 * id-ja u zahtevu. Jedan uređaj ili sesija ima najviše jednu pretplatu, isti endpoint postoji najviše jednom
 * ({@code endpoint_hes} = SHA-256). Pun endpoint je tajna adresa za slanje: nikad u log (samo host i prvih 8 hex heša).
 * Id-jevi vlasnika bez veza: čitaju se samo kroz upite (važenje uređaja proverava pošiljalac).
 */
@Entity
@Table(name = "push_pretplate")
@Getter @Setter @NoArgsConstructor
@DynamicUpdate
public class PushPretplata {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 1000)
    private String endpoint;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "endpoint_hes", nullable = false, length = 64, unique = true)
    private String endpointHes;
    /** Javni ključ browsera (P-256, nekompresovana tačka, 65 bajtova), base64url bez dopune. */
    @Column(nullable = false, length = 100)
    private String p256dh;
    /** Tajna za autentikaciju (16 bajtova), base64url bez dopune. */
    @Column(nullable = false, length = 40)
    private String auth;
    @Column(name = "studentski_uredjaj_id")
    private Long studentskiUredjajId;
    @Column(name = "nastavnicka_sesija_id")
    private Long nastavnickaSesijaId;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    private LocalDateTime poslednjiUspeh;
    @Column(nullable = false)
    private int uzastopneGreske;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PushPretplata that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return PushPretplata.class.hashCode();
    }
}
