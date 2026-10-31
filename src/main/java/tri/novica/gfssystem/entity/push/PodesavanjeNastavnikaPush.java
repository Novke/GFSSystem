package tri.novica.gfssystem.entity.push;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Da li vrsta nastavničkog obaveštenja ({@code VrstaObavestenja}) ide i kao push na nastavnikove uređaje (spec aplikacije
 * 6.6). Red po vrsti, tek posle prve izmene; bez reda je uključeno.
 */
@Entity
@Table(name = "podesavanja_nastavnika_push")
@Getter @Setter @NoArgsConstructor
public class PodesavanjeNastavnikaPush {
    @Id
    @Column(nullable = false, length = 40)
    private String vrsta;
    @Column(nullable = false)
    private boolean ukljuceno = true;
    @Column(nullable = false)
    private LocalDateTime izmenjeno;
}
