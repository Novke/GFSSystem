package tri.novica.gfssystem.entity.cas;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tri.novica.gfssystem.entity.Predavanje;

import java.time.LocalDateTime;

/**
 * Ključ čekanja: student je skenirao važeći QR pre prijave (spec 4.2 tačka 3). U bazi je samo SHA-256 ključa; važi do
 * {@code istice} (15 min od skeniranja) i jednom ({@code iskorisceno}). Čekiranje iz ključa dobija {@code vreme =
 * skenirano}. Briše ga {@code CiscenjePrijave} posle isteka ili sat posle upotrebe. {@code uredjajHash}: SHA-256
 * kolačića {@code gfs_uredjaj} telefona koji je skenirao; potvrda važi samo sa istog telefona (ključ se ne prenosi), a
 * čekiranje dobija tu oznaku, pa "isti telefon kao X" otkriva ko deli ključeve.
 */
@Entity
@Table(name = "cekiranja_na_cekanju")
@Getter @Setter @NoArgsConstructor
public class CekiranjeNaCekanju {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64)
    private String kljucHash;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Predavanje predavanje;
    @Column(nullable = false)
    private LocalDateTime skenirano;
    @Column(nullable = false)
    private LocalDateTime istice;
    private LocalDateTime iskorisceno;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64, nullable = false)
    private String uredjajHash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof CekiranjeNaCekanju that && kljucHash != null && kljucHash.equals(that.getKljucHash());
    }

    @Override
    public int hashCode() {
        return CekiranjeNaCekanju.class.hashCode();
    }
}
