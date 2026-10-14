package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Beleška nastavnika o studentu (tabela {@code beleske}, Flyway V4). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "beleske")
public class Beleska {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_beleske_student"))
    private Student student;
    @Column(nullable = false, length = 2000)
    private String tekst;
    @Column(nullable = false)
    private LocalDateTime kreirano;
    private LocalDateTime izmenjeno;
}
