package tri.novica.gfssystem.entity.anketa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Folder sačuvanih anketa ({@code ankete_folderi}); redosled po {@code rb}, pa {@code id}. */
@Entity
@Table(name = "ankete_folderi")
@Getter @Setter @NoArgsConstructor
public class AnketaFolder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 60)
    private String naziv;
    @Column(nullable = false)
    private int rb;
    @Column(nullable = false)
    private LocalDateTime kreirano;
}
