package tri.novica.gfssystem.entity.banka;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tri.novica.gfssystem.entity.uzivo.Medij;

/** Prilog varijante (slika ili PDF) koji LaTeX tekst uključuje po imenu; ime je jedinstveno u varijanti. */
@Entity
@Table(name = "varijanta_prilozi")
@Getter @Setter @NoArgsConstructor
public class VarijantaPrilog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Varijanta varijanta;
    @Column(nullable = false, length = 60)
    private String ime;
    @ManyToOne(optional = false)
    private Medij medij;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof VarijantaPrilog that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return VarijantaPrilog.class.hashCode();
    }
}
