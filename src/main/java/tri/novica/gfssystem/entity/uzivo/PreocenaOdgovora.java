package tri.novica.gfssystem.entity.uzivo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Trag jedne izmene ocene odgovora posle časa: šta je bilo, šta je postalo i zašto. Briše se sa odgovorom (FK u bazi). */
@Entity
@Table(name = "preocene_odgovora")
@Getter
@Setter
@NoArgsConstructor
public class PreocenaOdgovora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long odgovorId;
    private Boolean staroTacno;
    @Column(nullable = false)
    private boolean novoTacno;
    @Column(columnDefinition = "text")
    private String staraPolja;

    @Column(name = "stari_poeni", nullable = false)
    private int stariPoeni;
    @Column(nullable = false, length = 300)
    private String razlog;
    @Column(length = 60)
    private String serija;
    @Column(nullable = false)
    private LocalDateTime kreirano;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PreocenaOdgovora that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return PreocenaOdgovora.class.hashCode();
    }
}
