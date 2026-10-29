package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.portal.StatistikaTipa;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;

import java.time.LocalDate;
import java.util.*;

/**
 * Statistika vežbanja za Novicu (spec studentskog dela 5.6, odluka 9): samo zbirno po tipu, bez spiska po studentu.
 * Filteri: grupa (sopstvena grupa studenta) i period izvlačenja ({@code od} i {@code do} uključeni, po danu).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatistikaVezbanja {

    private final NamedParameterJdbcTemplate jdbc;
    private final KandidatiZadatka kandidati;
    private final PredmetRepository predmetRepository;

    public List<StatistikaTipa> statistika(Long predmetId, Long grupaId, LocalDate od, LocalDate doDana) {
        if (predmetId == null || !predmetRepository.existsById(predmetId)) {
            throw new SystemException("Predmet ne postoji! ID = " + predmetId, HttpStatus.NOT_FOUND);
        }
        if (od != null && doDana != null && doDana.isBefore(od)) {
            throw new SystemException("Kraj perioda je pre početka.", HttpStatus.BAD_REQUEST);
        }
        MapSqlParameterSource p = new MapSqlParameterSource("p", predmetId).addValue("g", grupaId)
                .addValue("od", od == null ? null : od.atStartOfDay())
                .addValue("do", doDana == null ? null : doDana.plusDays(1).atStartOfDay());
        List<Object[]> redovi = jdbc.query("""
                SELECT v.grupa_zadataka_id, gz.naziv, COUNT(DISTINCT v.student_id), COUNT(*),
                       SUM(CASE WHEN v.broj_provera > 0 THEN 1 ELSE 0 END),
                       SUM(CASE WHEN v.prva_provera_tacno = 1 THEN 1 ELSE 0 END),
                       SUM(CASE WHEN v.resenje_prikazano = 1 AND (v.tacno IS NULL OR v.tacno = 0) THEN 1 ELSE 0 END)
                FROM vezbanja v
                JOIN studenti s ON s.id = v.student_id
                JOIN grupe_zadataka gz ON gz.id = v.grupa_zadataka_id
                WHERE gz.predmet_id = :p AND (:g IS NULL OR s.grupa_id = :g)
                  AND (:od IS NULL OR v.izvuceno >= :od) AND (:do IS NULL OR v.izvuceno < :do)
                GROUP BY v.grupa_zadataka_id, gz.naziv""", p, (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2),
                rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7)});
        if (redovi.isEmpty()) return List.of();
        StabloPredavanja stablo = kandidati.stablo(predmetId);
        Map<Long, Integer> redosled = new HashMap<>();
        List<Long> redom = stablo.redom();
        for (int i = 0; i < redom.size(); i++) redosled.put(redom.get(i), i);
        List<StatistikaTipa> rez = new ArrayList<>();
        for (Object[] r : redovi) {
            Long id = (Long) r[0];
            Opis o = stablo.opis(id);
            List<String> putanja = o == null ? List.of()
                    : o.putanja().subList(0, o.putanja().size() - 1).stream().map(GrupaPutanja::naziv).toList();
            rez.add(new StatistikaTipa(id, (String) r[1], putanja, (Integer) r[2], (Integer) r[3], (Integer) r[4],
                    (Integer) r[5], (Integer) r[6]));
        }
        rez.sort(Comparator.comparingInt(s -> redosled.getOrDefault(s.tipId(), Integer.MAX_VALUE)));
        return rez;
    }
}
