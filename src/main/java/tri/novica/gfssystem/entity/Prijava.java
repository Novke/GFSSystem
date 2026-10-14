package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "prijave")
public class Prijava {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private OnboardingSesija sesija;
    @Column(nullable = false, length = 60)
    private String ime;
    @Column(nullable = false, length = 60)
    private String prezime;
    @Column(nullable = false, length = 20)
    private String indeks;
    @Column(nullable = false)
    private int godina;
    @Column(nullable = false, length = 120)
    private String email;
    @Column(nullable = false, length = 20)
    private String brojTelefona;
    private LocalDate datumRodjenja;
    @Column(length = 100)
    private String opstina;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPrijave status;
    @Column(nullable = false)
    private LocalDateTime podneto;
    private LocalDateTime obradjeno;
    @ManyToOne
    private Student student;
    @Column(length = 255)
    private String napomena;
}
