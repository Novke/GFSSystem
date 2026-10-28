package tri.novica.gfssystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@Table(name = "predavanja",
        uniqueConstraints = @UniqueConstraint(columnNames = {"rb", "grupa_id"}))
public class Predavanje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @NonNull
    private Predmet predmet;
    @ManyToOne
    private Grupa grupa;

    private int rb;
    private LocalDate datum;
    private String tema;
    private Integer posecenost = 0;
    private Boolean zavrseno = false;
    /**
     * HMAC tajna QR-a za čekiranje (32 znaka iz {@code TokenGenerator}); nastaje kad nastavnik prvi put otvori QR, briše
     * se kad se predavanje završi i menja se kad je starija od 5 h ({@code cekiranjeTajnaOd}). Nikad ne izlazi iz servera.
     */
    @JsonIgnore @ToString.Exclude
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 32)
    private String cekiranjeTajna;
    @JsonIgnore
    private LocalDateTime cekiranjeTajnaOd;

    @OneToMany(mappedBy = "predavanje", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<Domaci> domaci = new HashSet<>();
    @OneToMany(mappedBy = "predavanje", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE, CascadeType.DETACH})
    private Set<Aktivnost> aktivnosti = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Predavanje that = (Predavanje) o;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(predmet, that.predmet) && Objects.equals(grupa, that.grupa) && rb == that.rb;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public void umanjiPosecenost() {
        if (posecenost == null || posecenost <= 0) {
            posecenost=0;
            return;
        }
        posecenost--;
    }

    public void uvecajPosecenost(){
        if (posecenost == null) posecenost = 0;
        posecenost++;
    }

    public void izracunajPOsecenost(){
        posecenost = aktivnosti == null ? 0 : aktivnosti.size();
    }
}
