package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.portal.PredavanjeStudenta;
import tri.novica.gfssystem.dto.portal.PredmetStudenta;
import tri.novica.gfssystem.dto.portal.PrisustvoStudenta;
import tri.novica.gfssystem.dto.portal.TemaVezbanja;
import tri.novica.gfssystem.entity.TipAktivnosti;
import tri.novica.gfssystem.service.portal.KontekstStudenta.Kontekst;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tab "Predmeti" i istorija prisustva na nalogu (spec studentskog dela 5.2, 12.2). Spisak predavanja studenta za predmet:
 * predavanja sopstvene grupe i predavanja drugih grupa na kojima ima aktivnost ili čekiranje. "Prisutan" = bilo koja
 * sopstvena aktivnost. Nikad napomene uz aktivnost, tuđe aktivnosti ni bodovi.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PredmetiNaNalogu {

    private final KontekstStudenta kontekstStudenta;
    private final VezbanjeStudenta vezbanje;
    private final ObjavaOcenaServis objavaOcena;
    private final NamedParameterJdbcTemplate jdbc;

    public List<PredmetStudenta> predmeti(long studentId) {
        Kontekst k = kontekstStudenta.kontekst(studentId);
        List<PredmetStudenta> rez = new ArrayList<>();
        for (KontekstStudenta.Predmet p : kontekstStudenta.predmeti(k)) {
            List<PredavanjeStudenta> predavanja = predavanja(k, p.id());
            int prisutan = (int) predavanja.stream().filter(x -> x.aktivnost() != null).count();
            int tipova = vezbanje.stablo(k, p.id()).stream().mapToInt(t -> t.tipovi().size()).sum();
            rez.add(new PredmetStudenta(p.id(), p.naziv(), prisutan, predavanja.size(), tipova,
                    objavaOcena.objavljenoZa(k, p.id())));
        }
        return rez;
    }

    public PrisustvoStudenta prisustvo(long studentId, Long predmetId) {
        Kontekst k = kontekstStudenta.zaPredmet(studentId, predmetId);
        List<PredavanjeStudenta> predavanja = predavanja(k, predmetId);
        int prisutan = (int) predavanja.stream().filter(x -> x.aktivnost() != null).count();
        return new PrisustvoStudenta(prisutan, predavanja.size(), predavanja);
    }

    /** Predavanja studenta za predmet, od najnovijeg; redni broj je mesto predavanja u svojoj grupi (po datumu). */
    List<PredavanjeStudenta> predavanja(Kontekst k, Long predmetId) {
        MapSqlParameterSource p = new MapSqlParameterSource("s", k.studentId()).addValue("p", predmetId)
                .addValue("svoja", k.svojaGrupa() == null ? -1L : k.svojaGrupa());
        return jdbc.query("""
                SELECT p.id, p.datum, p.tema, p.grupa_id, g.naziv AS grupa_naziv,
                  (SELECT COUNT(*) FROM predavanja p2 WHERE p2.grupa_id = p.grupa_id AND p2.predmet_id = p.predmet_id
                     AND (COALESCE(p2.datum, '1000-01-01') < COALESCE(p.datum, '1000-01-01')
                          OR (COALESCE(p2.datum, '1000-01-01') = COALESCE(p.datum, '1000-01-01') AND p2.id <= p.id))) AS redni,
                  (SELECT a.tip FROM aktivnosti a WHERE a.predavanje_id = p.id AND a.student_id = :s
                     ORDER BY FIELD(a.tip, 'SA_ZVEZDICOM', 'ZADATAK', 'PRISUSTVO'), a.id LIMIT 1) AS tip,
                  (SELECT c.vreme FROM cekiranja c WHERE c.predavanje_id = p.id AND c.student_id = :s) AS cekiran
                FROM predavanja p LEFT JOIN grupe g ON g.id = p.grupa_id
                WHERE p.predmet_id = :p
                  AND (p.grupa_id = :svoja
                       OR EXISTS (SELECT 1 FROM aktivnosti a WHERE a.predavanje_id = p.id AND a.student_id = :s)
                       OR EXISTS (SELECT 1 FROM cekiranja c WHERE c.predavanje_id = p.id AND c.student_id = :s))
                ORDER BY p.datum DESC, p.id DESC""", p, (rs, i) -> {
            String tip = rs.getString("tip");
            TipAktivnosti aktivnost = null;
            if (tip != null) {
                try {
                    aktivnost = TipAktivnosti.valueOf(tip);
                } catch (IllegalArgumentException e) {
                    aktivnost = TipAktivnosti.PRISUSTVO;   // ima aktivnost nepoznatog tipa: bio je
                }
            }
            Long grupa = (Long) rs.getObject("grupa_id", Long.class);
            return new PredavanjeStudenta(rs.getLong("id"), rs.getObject("datum", java.time.LocalDate.class),
                    rs.getInt("redni"), rs.getString("tema"), rs.getString("grupa_naziv"),
                    Objects.equals(grupa, k.svojaGrupa()), aktivnost,
                    rs.getObject("cekiran", java.time.LocalDateTime.class));
        });
    }

    /** Za vežbanje na strani predmeta (isti kontekst, provera predmeta). */
    public List<TemaVezbanja> vezbanje(long studentId, Long predmetId) {
        return vezbanje.stablo(studentId, predmetId);
    }
}
