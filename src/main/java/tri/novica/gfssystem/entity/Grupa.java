package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "grupe")
public class Grupa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String naziv;
    private Integer godinaUpisa;
    /** Link za poziv u WhatsApp grupu, uvek u obliku {@code https://chat.whatsapp.com/<kod>} (vidi {@code WaLink}); null = nije podešen. */
    @Column(length = 100)
    private String waLink;

    @OneToMany(cascade = CascadeType.PERSIST, mappedBy = "grupa")
    private List<Student> studenti;
    @OneToMany(mappedBy = "grupa")
    private List<Predavanje> predavanja;
    @OneToMany(mappedBy = "grupa")
    private List<Domaci> domaci;

}
