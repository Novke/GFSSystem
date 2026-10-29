package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Grupe studenta (spec studentskog dela 5.1, 7.2): sopstvena grupa plus grupe predavanja na kojima student ima aktivnost
 * ili čekiranje (student koji ponavlja predmet uz mlađu generaciju). Jedini izvor za sve studentske upite faza 2 i 3: nijedan
 * upit ne čita {@code student.grupa} sam. Student se uvek uzima iz sesije, nikad iz zahteva.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrupeStudenta {

    private final JdbcTemplate jdbc;

    /** Grupe studenta, prvo sopstvena; prazno za nepostojećeg studenta ili studenta bez grupe i bez nastave. */
    public Set<Long> za(long studentId) {
        Set<Long> grupe = new LinkedHashSet<>();
        grupe.addAll(jdbc.queryForList(
                "SELECT s.grupa_id FROM studenti s WHERE s.id = ? AND s.grupa_id IS NOT NULL", Long.class, studentId));
        grupe.addAll(jdbc.queryForList("""
                SELECT DISTINCT p.grupa_id FROM aktivnosti a JOIN predavanja p ON p.id = a.predavanje_id
                WHERE a.student_id = ? AND p.grupa_id IS NOT NULL
                UNION
                SELECT DISTINCT p.grupa_id FROM cekiranja c JOIN predavanja p ON p.id = c.predavanje_id
                WHERE c.student_id = ? AND p.grupa_id IS NOT NULL""", Long.class, studentId, studentId));
        return grupe;
    }
}
