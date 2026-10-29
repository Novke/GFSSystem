package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.*;

/**
 * Kontekst studenta za studentske endpointe faza 2 i 3: id iz sesije, sopstvena grupa i sve grupe ({@link GrupeStudenta}),
 * i predmeti koje sluša (bar jedno predavanje neke od tih grupa). Tuđ ili nepostojeći predmet je isti 404 (spec 7.2).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KontekstStudenta {

    /** Jedna poruka za svaki tuđ ili nepostojeći objekat na nalogu: ne otkriva da li postoji. */
    public static final String NEMA = "Nije pronađeno.";

    private final GrupeStudenta grupeStudenta;
    private final NamedParameterJdbcTemplate jdbc;

    /** {@code svojaGrupa} može biti {@code null} (student bez grupe); {@code grupe} prvo sopstvena. */
    public record Kontekst(long studentId, Long svojaGrupa, Set<Long> grupe) {
    }

    public record Predmet(Long id, String naziv) {
    }

    public Kontekst kontekst(long studentId) {
        Long svoja = jdbc.query("SELECT grupa_id FROM studenti WHERE id = :s", Map.of("s", studentId),
                rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null);
        return new Kontekst(studentId, svoja, grupeStudenta.za(studentId));
    }

    /** Predmeti koje student sluša, po nazivu. */
    public List<Predmet> predmeti(Kontekst k) {
        if (k.grupe().isEmpty()) return List.of();
        return jdbc.query("""
                SELECT pr.id, pr.naziv FROM predmeti pr
                WHERE pr.id IN (SELECT p.predmet_id FROM predavanja p WHERE p.grupa_id IN (:g))
                ORDER BY pr.naziv, pr.id""", new MapSqlParameterSource("g", k.grupe()),
                (rs, i) -> new Predmet(rs.getLong(1), rs.getString(2)));
    }

    /** Kontekst za predmet koji student sluša, inače 404 {@value #NEMA}. */
    public Kontekst zaPredmet(long studentId, Long predmetId) {
        Kontekst k = kontekst(studentId);
        if (predmetId == null || predmeti(k).stream().noneMatch(p -> p.id().equals(predmetId))) throw nema();
        return k;
    }

    public static SystemException nema() {
        return new SystemException(NEMA, HttpStatus.NOT_FOUND);
    }
}
