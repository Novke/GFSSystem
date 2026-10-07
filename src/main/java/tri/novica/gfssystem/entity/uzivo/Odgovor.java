package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Odgovor jednog učesnika u jednoj rundi (najviše jedan, {@code UNIQUE(runda_id, ucesnik_id)}). */
@Entity
@Table(name = "odgovori")
@Getter @Setter @NoArgsConstructor
public class Odgovor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private PitanjeRunda runda;
    @ManyToOne(optional = false)
    private Ucesnik ucesnik;
    /** Id-jevi izabranih opcija, odvojeni zarezom. */
    @Column(length = 100)
    private String opcije;
    private Double broj;
    @Column(length = 200)
    private String tekst;
    private Integer skala;
    /** {@code null} kad pitanje nema tačan odgovor (anketa, procena, skala). */
    private Boolean tacno;
    @Column(nullable = false)
    private int poeni;
    @Column(nullable = false)
    private long vremeMs;
    @Column(nullable = false)
    private boolean sakriven;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Odgovor that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Odgovor.class.hashCode();
    }
}
