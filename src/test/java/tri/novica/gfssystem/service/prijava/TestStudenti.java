package tri.novica.gfssystem.service.prijava;

import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.repository.StudentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Izmišljeni studenti za testove naloga (indeks nasumičan, mejl na {@code example.test}); {@link #obrisi()} ih briše
 * zajedno sa uređajima i kodovima (CASCADE) i redovima {@code pokusaji} po id-ju studenta.
 */
public final class TestStudenti {

    private final StudentRepository studenti;
    private final JdbcTemplate jdbc;
    private final List<Long> napravljeni = new ArrayList<>();

    public TestStudenti(StudentRepository studenti, JdbcTemplate jdbc) {
        this.studenti = studenti;
        this.jdbc = jdbc;
    }

    /** Nasumičan indeks ({@code T7-<8 znakova>/2025}), već normalizovan. */
    public static String noviIndeks() {
        return "T7-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT) + "/2025";
    }

    /** Student sa datim mejlom ({@code null} = bez mejla). */
    public Student napravi(String email) {
        return napravi(noviIndeks(), 2025, email);
    }

    public Student napravi(String indeks, int godina, String email) {
        Student s = new Student();
        s.setIme("Test");
        s.setPrezime("Studentić");
        s.setIndeks(indeks);
        s.setGodina(godina);
        s.setEmail(email);
        Student sacuvan = studenti.save(s);
        napravljeni.add(sacuvan.getId());
        return sacuvan;
    }

    public void obrisi() {
        for (Long id : napravljeni) {
            jdbc.update("delete from pokusaji where kljuc = ?", String.valueOf(id));
            jdbc.update("delete from studenti where id = ?", id);
        }
        napravljeni.clear();
    }
}
