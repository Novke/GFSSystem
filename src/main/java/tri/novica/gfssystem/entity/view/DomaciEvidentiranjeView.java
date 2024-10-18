package tri.novica.gfssystem.entity.view;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import tri.novica.gfssystem.entity.TipAktivnosti;

@Data
@NoArgsConstructor
@Entity
@Table(name = "domacievidentiranjeview") //MORA MALIM SLOVIMA!!!
@Immutable
public class DomaciEvidentiranjeView {
    @Id
    @Column(name = "id")
    private Long studentId;
    @Column(name = "domaci_id")
    private Long domaciId;
    @Column(name = "ime")
    private String ime;
    @Column(name = "prezime")
    private String prezime;
    @Column(name = "indeks")
    private String indeks;
    @Column(name = "godina")
    private Integer godina;
    @Enumerated(EnumType.STRING)
    @Column(name = "tip")
    private TipAktivnosti tip;
    @Column(name = "predavanja_napomene")
    private String predavanjaNapomene;
    @Column(name = "uradjen_domaci_id")
    private Long uradjenDomaciId;
    @Column(name = "bodovi")
    private Integer bodovi;
    @Column(name = "uradjen_domaci_napomene")
    private String uradjenDomaciNapomene;
    @Column(name = "prepisivanje")
    private Boolean prepisivanje;
}
