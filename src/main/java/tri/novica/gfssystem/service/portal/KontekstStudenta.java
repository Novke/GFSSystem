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
 * Kontekst studenta za studentske endpointe faza 2 i 3: id iz sesije, sopstvena grupa, dodatne grupe po predmetu
 * ({@link GrupeStudenta}) i predmeti koje sluša (predavanje sopstvene grupe ili predavanje na kome ima aktivnost ili
 * čekiranje). Tuđ ili nepostojeći predmet je isti 404 (spec 7.2).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KontekstStudenta {

    /** Jedna poruka za svaki tuđ ili nepostojeći objekat na nalogu: ne otkriva da li postoji. */
    public static final String NEMA = "Nije pronađeno.";

    private final GrupeStudenta grupeStudenta;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * {@code svojaGrupa} može biti {@code null} (student bez grupe); {@code dodatne} su grupe po predmetu (predmet ->
     * grupe predavanja tog predmeta na kojima je student bio). Grupe za upit uvek idu preko {@link #grupe(Long)}.
     */
    public record Kontekst(long studentId, Long svojaGrupa, Map<Long, Set<Long>> dodatne) {

        /** Grupe studenta za predmet, prvo sopstvena: samo one važe za teme, slajdove i zadatke tog predmeta. */
        public Set<Long> grupe(Long predmetId) {
            Set<Long> g = new LinkedHashSet<>();
            if (svojaGrupa != null) g.add(svojaGrupa);
            if (predmetId != null) g.addAll(dodatne.getOrDefault(predmetId, Set.of()));
            return g;
        }
    }

    public record Predmet(Long id, String naziv) {
    }

    public Kontekst kontekst(long studentId) {
        return new Kontekst(studentId, grupeStudenta.svoja(studentId), grupeStudenta.dodatne(studentId));
    }

    /** Predmeti koje student sluša, po nazivu: predavanje sopstvene grupe ili predmet sa dodatnom grupom. */
    public List<Predmet> predmeti(Kontekst k) {
        if (k.svojaGrupa() == null && k.dodatne().isEmpty()) return List.of();
        MapSqlParameterSource p = new MapSqlParameterSource("svoja", k.svojaGrupa() == null ? -1L : k.svojaGrupa())
                .addValue("dodatni", k.dodatne().isEmpty() ? List.of(-1L) : k.dodatne().keySet());
        return jdbc.query("""
                SELECT pr.id, pr.naziv FROM predmeti pr
                WHERE pr.id IN (SELECT p.predmet_id FROM predavanja p WHERE p.grupa_id = :svoja)
                   OR pr.id IN (:dodatni)
                ORDER BY pr.naziv, pr.id""", p, (rs, i) -> new Predmet(rs.getLong(1), rs.getString(2)));
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
