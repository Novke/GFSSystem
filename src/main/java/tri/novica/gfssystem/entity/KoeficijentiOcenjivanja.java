package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "koeficijenti_ocenjivanja")
public class KoeficijentiOcenjivanja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private Predmet predmet;

    // Koeficijenti za aktivnosti
    private Double koefPrisustvo = 1.0;
    private Double koefZadatak = 2.0;
    private Double koefZvezdica = 4.0;

    // Koeficijenti za domace
    private Double domaciFlat = 4.0;      // fiksni poeni po domacem
    private Double domaciVarijansa = 6.0; // varijabilni poeni (od bodova 0-10)

    // Opcije izracunavanja
    private Boolean koristiMaxRezultat = true;  // true=MAX, false=POSLEDNJI
    private Boolean prikaziZbirno = false;      // true=zbirno predispitne, false=razdvojeno domaci/aktivnost

    // Maksimumi za normalizaciju (ako null, ne normalizuje se)
    private Double maxAktivnost;
    private Double maxDomaci;

    @OneToMany(mappedBy = "koeficijenti", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KoeficijentTipTesta> koeficijentiTipova = new ArrayList<>();

    public KoeficijentiOcenjivanja(Predmet predmet) {
        this.predmet = predmet;
    }
}
