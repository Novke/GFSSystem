package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ponuđeni odgovor na pitanje sa opcijama; {@code rb} određuje redosled. */
@Entity
@Table(name = "pitanje_opcije")
@Getter @Setter @NoArgsConstructor
public class PitanjeOpcija {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Pitanje pitanje;
    @Column(nullable = false)
    private int rb;
    @Column(nullable = false, length = 300)
    private String tekst;
    @Column(nullable = false)
    private boolean tacna;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PitanjeOpcija that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return PitanjeOpcija.class.hashCode();
    }
}
