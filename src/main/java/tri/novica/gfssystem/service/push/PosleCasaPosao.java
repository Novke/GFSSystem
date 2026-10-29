package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.entity.portal.OtvorenaTema;
import tri.novica.gfssystem.entity.push.VrstaStudentskogObavestenja;
import tri.novica.gfssystem.repository.portal.OtvorenaTemaRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;
import tri.novica.gfssystem.service.portal.KontekstStudenta;
import tri.novica.gfssystem.service.portal.KontekstStudenta.Kontekst;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis.Prekidac;

import java.time.*;
import java.util.*;

/**
 * Zbirno obaveštenje "posle časa" (spec aplikacije 6.4), posao na 5 min ({@code gfs.push.posle-casa.cron}, {@code -}
 * gasi). Zakazivanje je u bazi ({@code posle_casa_poslato}, red po grupi i danu, {@code TemeZaVezbanje.krajIzvodjenja}): 30
 * min posle kraja poslednjeg izvođenja sa grupom, pa preživljava restart. Dospeo red se obrađuje jednom (pod zaključanim
 * redom, {@code poslato} se upisuje u istoj transakciji sa redovima zvona):
 * <ul>
 *   <li>dok grupa ima AKTIVNO izvođenje, čeka (njegov kraj pomera rok);</li>
 *   <li>samo istog dana i pre 22:00 (i ne pre 07:00); propušteno se zatvara bez slanja;</li>
 *   <li>"novo" = vidljivo grupi prvi put tog dana (bez novog red se briše, pa kasnije izvođenje istog dana zakazuje
 *       ponovo): prezentacije sa slajdom koji grupa ranije nije videla
 *       ({@code prikazani_slajdovi} završenih izvođenja) i tipovi za vežbanje (listovi sa kandidatima) koje otvorene teme
 *       grupe ranije nisu pokrivale ({@code otvorene_teme.prvi_put}); samo predmeti tog dana; bez novog nema obaveštenja;</li>
 *   <li>primaoci: studenti koji te stvari vide na nalogu (grupa je njihova za predmet i slušaju predmet), i oni koji nisu
 *       bili na času; red zvona {@code POSLE_CASA} po studentu, push po prekidaču {@code push_posle_casa}, TTL 24 h.</li>
 * </ul>
 */
@Component
@Slf4j
public class PosleCasaPosao {

    static final LocalTime TIHO_OD = LocalTime.of(22, 0);
    static final LocalTime TIHO_DO = LocalTime.of(7, 0);
    static final Duration TTL = Duration.ofHours(24);

    /** Rezultat obrade jednog reda: ključ i studenti kojima je red zvona sada upisan. */
    record Obrada(String kljuc, Set<Long> novi) {
        static final Obrada NISTA = new Obrada(null, Set.of());
    }

    /** Novo za grupu u jednom predmetu. */
    record Novo(int prezentacija, int tipova) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final KontekstStudenta kontekst;
    private final KandidatiZadatka kandidati;
    private final OtvorenaTemaRepository otvorene;
    private final StudentskaObavestenja obavestenja;
    private final TransactionTemplate transakcija;
    private final Clock clock;

    public PosleCasaPosao(NamedParameterJdbcTemplate jdbc, KontekstStudenta kontekst, KandidatiZadatka kandidati,
                          OtvorenaTemaRepository otvorene, StudentskaObavestenja obavestenja,
                          PlatformTransactionManager transakcije, Clock clock) {
        this.jdbc = jdbc;
        this.kontekst = kontekst;
        this.kandidati = kandidati;
        this.otvorene = otvorene;
        this.obavestenja = obavestenja;
        this.transakcija = new TransactionTemplate(transakcije);
        this.transakcija.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clock = clock;
    }

    @Scheduled(cron = "${gfs.push.posle-casa.cron:30 */5 * * * *}")
    public void zakazano() {
        try {
            posalji();
        } catch (RuntimeException e) {
            log.error("Obaveštenje posle časa nije obrađeno: {}", e.getClass().getSimpleName());
        }
    }

    /** Jedan prolaz kroz dospele redove; vraća broj studenata kojima je obaveštenje sada upisano. */
    public int posalji() {
        LocalDateTime sada = LocalDateTime.now(clock);
        List<Map<String, Object>> dospeli = jdbc.queryForList("""
                SELECT grupa_id, datum FROM posle_casa_poslato WHERE poslato IS NULL AND dospeva <= :sada
                ORDER BY dospeva, grupa_id""", new MapSqlParameterSource("sada", sada));
        int ukupno = 0;
        for (Map<String, Object> r : dospeli) {
            Long grupaId = ((Number) r.get("grupa_id")).longValue();
            LocalDate datum = datum(r.get("datum"));
            try {
                Obrada o = transakcija.execute(s -> obradi(grupaId, datum, sada));
                if (o != null && !o.novi().isEmpty()) {
                    int push = obavestenja.posalji(o.kljuc(), o.novi(), Prekidac.PUSH_POSLE_CASA, TTL, false);
                    log.info("Posle časa: grupa={}, datum={}, studenata={}, push poruka={}", grupaId, datum, o.novi().size(),
                            push);
                    ukupno += o.novi().size();
                }
            } catch (RuntimeException e) {
                log.error("Posle časa nije obrađeno, grupa={}, datum={}: {}", grupaId, datum, e.getClass().getSimpleName());
            }
        }
        return ukupno;
    }

    static String kljuc(Long grupaId, LocalDate datum) {
        return "POSLE_CASA:" + grupaId + ":" + datum;
    }

    private Obrada obradi(Long grupaId, LocalDate datum, LocalDateTime sada) {
        MapSqlParameterSource p = new MapSqlParameterSource("g", grupaId).addValue("d", datum).addValue("sada", sada);
        List<Map<String, Object>> red = jdbc.queryForList(
                "SELECT poslato FROM posle_casa_poslato WHERE grupa_id = :g AND datum = :d FOR UPDATE", p);
        if (red.isEmpty() || red.getFirst().get("poslato") != null) {
            return Obrada.NISTA;
        }
        Integer aktivnih = jdbc.queryForObject(
                "SELECT COUNT(*) FROM izvodjenja WHERE grupa_id = :g AND status = 'AKTIVNO'", p, Integer.class);
        if (aktivnih != null && aktivnih > 0) {
            return Obrada.NISTA;   // čeka kraj izvođenja koje je u toku (njegov kraj pomera rok)
        }
        LocalTime vreme = sada.toLocalTime();
        if (!sada.toLocalDate().equals(datum) || !vreme.isBefore(TIHO_OD)) {
            zatvori(p);
            log.info("Posle časa propušteno (prošao dan ili 22:00), grupa={}, datum={}", grupaId, datum);
            return Obrada.NISTA;
        }
        if (vreme.isBefore(TIHO_DO)) {
            return Obrada.NISTA;
        }
        Map<Long, Novo> poPredmetu = novo(grupaId, datum, sada);
        if (poPredmetu.isEmpty()) {
            // ništa nije poslato: red se briše, pa kasnije izvođenje istog dana može da zakaže svoje
            jdbc.update("DELETE FROM posle_casa_poslato WHERE grupa_id = :g AND datum = :d", p);
            log.info("Posle časa: ništa novo, grupa={}, datum={}", grupaId, datum);
            return Obrada.NISTA;
        }
        zatvori(p);
        String kljuc = kljuc(grupaId, datum);
        Set<Long> novi = new LinkedHashSet<>();
        for (Long studentId : kandidati(grupaId, poPredmetu.keySet())) {
            Kontekst k = kontekst.kontekst(studentId);
            Set<Long> slusa = new HashSet<>();
            kontekst.predmeti(k).forEach(x -> slusa.add(x.id()));
            int prezentacija = 0;
            int tipova = 0;
            List<Long> predmeti = new ArrayList<>();
            for (Map.Entry<Long, Novo> e : poPredmetu.entrySet()) {
                if (slusa.contains(e.getKey()) && k.grupe(e.getKey()).contains(grupaId)) {
                    prezentacija += e.getValue().prezentacija();
                    tipova += e.getValue().tipova();
                    predmeti.add(e.getKey());
                }
            }
            if (prezentacija + tipova == 0) continue;
            PushPoruka poruka = PushTekstovi.posleCasa(datum, prezentacija, tipova,
                    predmeti.size() == 1 ? predmeti.getFirst() : null);
            if (obavestenja.upisi(studentId, VrstaStudentskogObavestenja.POSLE_CASA, kljuc, poruka)) {
                novi.add(studentId);
            }
        }
        return new Obrada(kljuc, novi);
    }

    private void zatvori(MapSqlParameterSource p) {
        jdbc.update("UPDATE posle_casa_poslato SET poslato = :sada WHERE grupa_id = :g AND datum = :d", p);
    }

    /**
     * Novo za grupu tog dana, po predmetu (samo predmeti izvođenja grupe tog dana): broj prezentacija čiji je bar jedan
     * slajd grupa prvi put videla od početka dana i broj tipova za vežbanje otvorenih prvi put od početka dana.
     */
    Map<Long, Novo> novo(Long grupaId, LocalDate datum, LocalDateTime sada) {
        LocalDateTime od = datum.atStartOfDay();
        MapSqlParameterSource p = new MapSqlParameterSource("g", grupaId).addValue("od", od)
                .addValue("do", datum.plusDays(1).atStartOfDay()).addValue("sada", sada);
        List<Long> predmeti = jdbc.queryForList("""
                SELECT DISTINCT pz.predmet_id FROM izvodjenja i JOIN prezentacije pz ON pz.id = i.prezentacija_id
                WHERE i.grupa_id = :g AND i.status = 'ZAVRSENO' AND i.pocetak >= :od AND i.pocetak < :do
                ORDER BY pz.predmet_id""", p, Long.class);
        if (predmeti.isEmpty()) return Map.of();
        p.addValue("predmeti", predmeti);
        Map<Long, Integer> prezentacija = new HashMap<>();
        jdbc.query("""
                SELECT pz.predmet_id, COUNT(DISTINCT s.prezentacija_id) FROM (
                  SELECT ps.slajd_id, MIN(COALESCE(i.kraj, i.pocetak)) AS prvi
                  FROM prikazani_slajdovi ps JOIN izvodjenja i ON i.id = ps.izvodjenje_id
                  WHERE ps.grupa_id = :g AND i.status = 'ZAVRSENO'
                  GROUP BY ps.slajd_id
                ) x JOIN slajdovi s ON s.id = x.slajd_id JOIN prezentacije pz ON pz.id = s.prezentacija_id
                WHERE x.prvi >= :od AND x.prvi <= :sada AND pz.predmet_id IN (:predmeti)
                GROUP BY pz.predmet_id""", p, rs -> {
            prezentacija.put(rs.getLong(1), rs.getInt(2));
        });
        List<OtvorenaTema> teme = otvorene.findAllByGrupaIdIn(List.of(grupaId));
        Map<Long, Novo> rez = new LinkedHashMap<>();
        for (Long predmetId : predmeti) {
            int tipova = 0;
            if (!teme.isEmpty()) {
                StabloPredavanja stablo = kandidati.stablo(predmetId);
                Set<Long> ranije = new HashSet<>();
                Set<Long> sve = new LinkedHashSet<>();
                for (OtvorenaTema t : teme) {
                    Opis o = stablo.opis(t.getTemaId());
                    if (o == null || t.getPrviPut().isAfter(sada)) continue;
                    sve.addAll(o.listovi());
                    if (t.getPrviPut().isBefore(od)) ranije.addAll(o.listovi());
                }
                for (Long list : sve) {
                    Opis o = stablo.opis(list);
                    if (!ranije.contains(list) && o != null && o.brojKandidata() > 0) tipova++;
                }
            }
            int prez = prezentacija.getOrDefault(predmetId, 0);
            if (prez + tipova > 0) rez.put(predmetId, new Novo(prez, tipova));
        }
        return rez;
    }

    /** Sopstveni studenti grupe i stariji koji su imali aktivnost ili čekiranje na predavanju grupe iz tih predmeta. */
    private List<Long> kandidati(Long grupaId, Collection<Long> predmeti) {
        MapSqlParameterSource p = new MapSqlParameterSource("g", grupaId).addValue("predmeti", predmeti);
        return jdbc.queryForList("""
                SELECT id FROM studenti WHERE grupa_id = :g
                UNION
                SELECT a.student_id FROM aktivnosti a JOIN predavanja pr ON pr.id = a.predavanje_id
                WHERE pr.grupa_id = :g AND pr.predmet_id IN (:predmeti)
                UNION
                SELECT c.student_id FROM cekiranja c JOIN predavanja pr ON pr.id = c.predavanje_id
                WHERE pr.grupa_id = :g AND pr.predmet_id IN (:predmeti)
                ORDER BY 1""", p, Long.class);
    }

    private static LocalDate datum(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString());
    }
}
