package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Grupe studenta (spec studentskog dela 5.1, 7.2): sopstvena grupa plus, <b>po predmetu</b>, grupe predavanja tog predmeta
 * na kojima student ima aktivnost ili čekiranje (student koji ponavlja predmet uz mlađu generaciju). Dodatna grupa važi
 * samo za predmet predavanja na kome je student bio: čekiranje kod mlađe generacije na predmetu P ne otvara njene teme,
 * slajdove ni predmete iz drugih predmeta. Jedini izvor za sve studentske upite faza 2 i 3: nijedan upit ne čita
 * {@code student.grupa} sam. Student se uvek uzima iz sesije, nikad iz zahteva.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrupeStudenta {

    private final JdbcTemplate jdbc;

    /** Sopstvena grupa studenta ili {@code null} (nepostojeći student ili student bez grupe). */
    public Long svoja(long studentId) {
        return jdbc.query("SELECT grupa_id FROM studenti WHERE id = ?",
                rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null, studentId);
    }

    /**
     * Dodatne grupe po predmetu: predmet predavanja -> grupe predavanja tog predmeta na kojima student ima aktivnost ili
     * čekiranje (može sadržati i sopstvenu grupu). Predmeti i grupe redom id-ja.
     */
    public Map<Long, Set<Long>> dodatne(long studentId) {
        Map<Long, Set<Long>> rez = new LinkedHashMap<>();
        jdbc.query("""
                SELECT x.predmet_id, x.grupa_id FROM (
                  SELECT p.predmet_id, p.grupa_id FROM aktivnosti a JOIN predavanja p ON p.id = a.predavanje_id
                  WHERE a.student_id = ? AND p.grupa_id IS NOT NULL
                  UNION
                  SELECT p.predmet_id, p.grupa_id FROM cekiranja c JOIN predavanja p ON p.id = c.predavanje_id
                  WHERE c.student_id = ? AND p.grupa_id IS NOT NULL
                ) x ORDER BY x.predmet_id, x.grupa_id""", rs -> {
            rez.computeIfAbsent(rs.getLong(1), k -> new LinkedHashSet<>()).add(rs.getLong(2));
        }, studentId, studentId);
        return rez;
    }

    /** Grupe studenta za predmet, prvo sopstvena; prazno za nepostojećeg studenta ili studenta bez grupe i bez nastave. */
    public Set<Long> za(long studentId, Long predmetId) {
        Set<Long> grupe = new LinkedHashSet<>();
        Long svoja = svoja(studentId);
        if (svoja != null) grupe.add(svoja);
        grupe.addAll(dodatne(studentId).getOrDefault(predmetId, Set.of()));
        return grupe;
    }
}
