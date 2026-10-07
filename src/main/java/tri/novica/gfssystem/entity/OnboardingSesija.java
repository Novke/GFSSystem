package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "onboarding_sesije")
public class OnboardingSesija {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private Grupa grupa;
    @Column(nullable = false, unique = true, length = 32)
    private String token;
    @Column(nullable = false)
    private boolean aktivna = true;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    @Column(nullable = false)
    private LocalDateTime istice;
    @Column(nullable = false)
    private int maxPrijava = 200;
    @Column(length = 255)
    private String napomena;
}
