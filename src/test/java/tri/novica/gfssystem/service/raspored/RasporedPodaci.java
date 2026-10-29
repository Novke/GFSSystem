package tri.novica.gfssystem.service.raspored;

import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.raspored.RasporedCmd;
import tri.novica.gfssystem.dto.raspored.RasporedDetails;
import tri.novica.gfssystem.dto.raspored.StavkaCmd;
import tri.novica.gfssystem.service.cas.CasPodaci;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Izmišljeni podaci za testove rasporeda: predmet, grupe, studenti i predavanja iz {@link CasPodaci} (JDBC, bez
 * {@code @Transactional}), plus periodi napravljeni kroz {@link RasporedService}. {@link #obrisi()} briše periode (stavke,
 * otkazivanja i najave idu kaskadno), obaveštenja njihovih stavki, čekiranja i sve iz {@link CasPodaci}.
 */
final class RasporedPodaci {

    final CasPodaci cas;
    private final JdbcTemplate jdbc;
    private final RasporedService rasporedi;
    private final List<Long> periodi = new ArrayList<>();
    private final List<Long> stavke = new ArrayList<>();

    RasporedPodaci(JdbcTemplate jdbc, RasporedService rasporedi) {
        this.jdbc = jdbc;
        this.rasporedi = rasporedi;
        this.cas = new CasPodaci(jdbc);
    }

    /** Period sa stavkama (svaka: grupa, dan, početak, kraj) za predmet ovih podataka. */
    RasporedDetails period(LocalDate od, LocalDate doDatuma, int prag, StavkaCmd... s) {
        RasporedDetails r = rasporedi.kreiraj(new RasporedCmd("Raspored " + cas.oznaka, od, doDatuma, prag, List.of(s)));
        periodi.add(r.id());
        r.stavke().forEach(x -> stavke.add(x.id()));
        return r;
    }

    StavkaCmd stavka(Long grupaId, int dan, String pocetak, String kraj) {
        return new StavkaCmd(null, cas.predmetId, grupaId, dan, LocalTime.parse(pocetak), LocalTime.parse(kraj));
    }

    void zapamtiStavke(RasporedDetails r) {
        r.stavke().forEach(x -> {
            if (!stavke.contains(x.id())) stavke.add(x.id());
        });
    }

    void mejl(Long studentId, String mejl) {
        jdbc.update("update studenti set email = ? where id = ?", mejl, studentId);
    }

    void cekiranje(Long predavanjeId, Long studentId) {
        jdbc.update("insert into cekiranja (predavanje_id, student_id, vreme) values (?, ?, ?)", predavanjeId, studentId,
                java.time.LocalDateTime.of(2030, 1, 1, 10, 0));
    }

    int brojObavestenja(String kljuc) {
        return jdbc.queryForObject("select count(*) from obavestenja where kljuc = ?", Integer.class, kljuc);
    }

    boolean reseno(String kljuc) {
        return jdbc.queryForObject("select count(*) from obavestenja where kljuc = ? and reseno is not null", Integer.class,
                kljuc) > 0;
    }

    void obrisi() {
        for (Long s : stavke) {
            jdbc.update("delete from obavestenja where kljuc like ?", "MNOGO_NAJAVA:" + s + ":%");
        }
        for (Long id : periodi) {
            jdbc.update("delete from rasporedi where id = ?", id);
        }
        jdbc.update("delete from cekiranja where predavanje_id in (select id from predavanja where predmet_id = ?)",
                cas.predmetId);
        cas.obrisi();
    }
}
