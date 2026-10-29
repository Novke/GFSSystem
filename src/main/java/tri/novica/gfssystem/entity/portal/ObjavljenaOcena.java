package tri.novica.gfssystem.entity.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Snimak predloga ocene jednog studenta u trenutku objave (JSON {@code SnimakOcene}). Student vidi samo svoj red, i to
 * samo dok objava postoji.
 */
@Entity
@Table(name = "objavljene_ocene")
@IdClass(ObjavljenaOcena.Kljuc.class)
@Getter @Setter @NoArgsConstructor
public class ObjavljenaOcena {
    @Id @Column(name = "objava_id")
    private Long objavaId;
    @Id @Column(name = "student_id")
    private Long studentId;
    @Column(nullable = false, columnDefinition = "mediumtext")
    private String snimak;

    /** Složen ključ (objava, student). */
    @lombok.Data @NoArgsConstructor @lombok.AllArgsConstructor
    public static class Kljuc implements Serializable {
        private Long objavaId;
        private Long studentId;
    }
}
