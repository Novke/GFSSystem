package tri.novica.gfssystem.entity.view;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import tri.novica.gfssystem.entity.TipAktivnosti;

@Data
@NoArgsConstructor
@Entity
@Table(name = "domacipredavanjastudentiview")
@Immutable
public class DomaciPredavanjaStudentiView {
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
    @Column(name = "napomene")
    private String napomene;
}
