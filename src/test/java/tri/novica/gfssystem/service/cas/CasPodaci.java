package tri.novica.gfssystem.service.cas;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Izmišljeni podaci za testove čekiranja, JDBC-om (bez {@code @Transactional}, da rade i paralelni zahtevi): predmet,
 * grupe, studenti i predavanja sa jedinstvenom oznakom; {@link #obrisi()} briše sve što je napravio (izvođenja i
 * prezentacije predmeta, aktivnosti, predavanja sa čekiranjima, studente, grupe i predmet). Vremena uvek iz Jave
 * ({@code LocalDateTime}), nikad {@code now()} MySQL-a (druga vremenska zona).
 */
public final class CasPodaci {

    public final String oznaka = UUID.randomUUID().toString().substring(0, 8);
    private final JdbcTemplate jdbc;
    private final List<Long> grupe = new ArrayList<>();
    private final List<Long> studenti = new ArrayList<>();
    private final List<Long> predavanja = new ArrayList<>();
    private int rb = 1;
    public final Long predmetId;

    public CasPodaci(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.update("insert into predmeti (naziv) values (?)", "Čas " + oznaka);
        predmetId = jdbc.queryForObject("select id from predmeti where naziv = ?", Long.class, "Čas " + oznaka);
    }

    public Long grupa(Integer godinaUpisa) {
        String naziv = "C-" + oznaka + "-" + (grupe.size() + 1);
        jdbc.update("insert into grupe (naziv, godina_upisa) values (?, ?)", naziv, godinaUpisa);
        Long id = jdbc.queryForObject("select id from grupe where naziv = ?", Long.class, naziv);
        grupe.add(id);
        return id;
    }

    /** Student grupe ({@code grupaId} null = bez grupe), godina upisa 2026. */
    public Long student(Long grupaId, String ime) {
        String indeks = "C" + oznaka + "-" + (studenti.size() + 1);
        jdbc.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values (?, 'Čekić', 2026, ?, ?)",
                ime, indeks, grupaId);
        Long id = jdbc.queryForObject("select id from studenti where indeks = ?", Long.class, indeks);
        studenti.add(id);
        return id;
    }

    /** Živo predavanje grupe ({@code null} = bez grupe) predmeta. */
    public Long predavanje(Long grupaId, LocalDate datum) {
        int r = 900_000 + Math.abs(oznaka.hashCode() % 50_000) * 10 + rb++;
        jdbc.update("insert into predavanja (predmet_id, grupa_id, rb, datum, tema, posecenost, zavrseno) values (?, ?, ?, ?, ?, 0, 0)",
                predmetId, grupaId, r, datum, "Tema " + oznaka);
        Long id = jdbc.queryForObject("select id from predavanja where predmet_id = ? and rb = ?", Long.class, predmetId, r);
        predavanja.add(id);
        return id;
    }

    public void zavrsi(Long predavanjeId) {
        jdbc.update("update predavanja set zavrseno = 1 where id = ?", predavanjeId);
    }

    public String tajna(Long predavanjeId) {
        return jdbc.queryForObject("select cekiranje_tajna from predavanja where id = ?", String.class, predavanjeId);
    }

    public void tajnaOd(Long predavanjeId, LocalDateTime od) {
        jdbc.update("update predavanja set cekiranje_tajna_od = ? where id = ?", od, predavanjeId);
    }

    public void vremeCekiranja(Long predavanjeId, Long studentId, LocalDateTime vreme) {
        jdbc.update("update cekiranja set vreme = ? where predavanje_id = ? and student_id = ?", vreme, predavanjeId, studentId);
    }

    public int brojCekiranja(Long predavanjeId) {
        return jdbc.queryForObject("select count(*) from cekiranja where predavanje_id = ?", Integer.class, predavanjeId);
    }

    public List<String> aktivnosti(Long predavanjeId, Long studentId) {
        return jdbc.queryForList("select tip from aktivnosti where predavanje_id = ? and student_id = ?", String.class,
                predavanjeId, studentId);
    }

    public int posecenost(Long predavanjeId) {
        return jdbc.queryForObject("select posecenost from predavanja where id = ?", Integer.class, predavanjeId);
    }

    public void aktivnost(Long predavanjeId, Long studentId, String tip) {
        jdbc.update("insert into aktivnosti (predavanje_id, student_id, tip) values (?, ?, ?)", predavanjeId, studentId, tip);
    }

    public void obrisi() {
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id in "
                + "(select id from prezentacije where predmet_id = ?)", predmetId);
        jdbc.update("delete from ucesnici where izvodjenje_id in (select i.id from izvodjenja i join prezentacije p "
                + "on p.id = i.prezentacija_id where p.predmet_id = ?)", predmetId);
        jdbc.update("delete from izvodjenja where prezentacija_id in (select id from prezentacije where predmet_id = ?)", predmetId);
        jdbc.update("delete from slajdovi where prezentacija_id in (select id from prezentacije where predmet_id = ?)", predmetId);
        jdbc.update("delete from prezentacije where predmet_id = ?", predmetId);
        for (Long id : predavanja) {
            jdbc.update("delete from aktivnosti where predavanje_id = ?", id);
            jdbc.update("delete from predavanja where id = ?", id);
        }
        for (Long id : studenti) {
            jdbc.update("delete from aktivnosti where student_id = ?", id);
            jdbc.update("delete from studenti where id = ?", id);
        }
        for (Long id : grupe) jdbc.update("delete from grupe where id = ?", id);
        jdbc.update("delete from predmeti where id = ?", predmetId);
    }
}
