package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.mejl.MejlPoruka;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mejl nastavniku uz obaveštenje (spec 6): samo kad je {@code gfs.nastavnik.mejl} podešen, posle commit-a, uz limite
 * {@code MEJL_NASTAVNIKU} (po vrsti i indeksu, 1/h) i {@code MEJL_NASTAVNIKU_UKUPNO} (20/h). Adresa je izmišljena.
 */
@SpringBootTest
@TestPropertySource(properties = "gfs.nastavnik.mejl=nastavnik@example.test")
@Import({PrijavaSatConfig.class, LazniMejlServis.class})
class ObavestenjeMejlIT {

    @Autowired ObavestenjeServis servis;
    @Autowired KodServis kodServis;
    @Autowired StudentRepository studentRepository;
    @Autowired LazniMejlServis mejl;
    @Autowired PomerljivSat sat;
    @Autowired PokusajiServis pokusaji;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;
    final List<String> kljucevi = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sat.postavi(Instant.now());
        mejl.ocisti();
        jdbc.update("delete from obavestenja");
        jdbc.update("delete from pokusaji where vrsta in ('MEJL_NASTAVNIKU', 'MEJL_NASTAVNIKU_UKUPNO')");
        studenti = new TestStudenti(studentRepository, jdbc);
    }

    @AfterEach
    void ocisti() {
        mejl.ocisti();
        studenti.obrisi();
        jdbc.update("delete from obavestenja");
        jdbc.update("delete from pokusaji where vrsta in ('MEJL_NASTAVNIKU', 'MEJL_NASTAVNIKU_UKUPNO')");
        for (String k : kljucevi) {
            jdbc.update("delete from pokusaji where kljuc = ?", k);
        }
    }

    String noviIp() {
        String ip = "t8-" + UUID.randomUUID().toString().substring(0, 8);
        kljucevi.add(ip);
        return ip;
    }

    Student student(String email) {
        Student s = studenti.napravi(email);
        kljucevi.add(kodServis.kljucIndeksa(s.getIndeks()));
        return s;
    }

    String nepostojeci() {
        String i = TestStudenti.noviIndeks();
        kljucevi.add(kodServis.kljucIndeksa(i));
        return i;
    }

    void pomoc(String indeks) {
        servis.neStizeKod(indeks, noviIp(), Optional.empty());
    }

    @Test
    void pomocSaljeMejlNastavnikuSaIndeksomILinkomBezImena() {
        Student s = student("ana@example.test");
        pomoc(s.getIndeks());

        MejlPoruka m = mejl.cekaj(1).getFirst();
        assertEquals("nastavnik@example.test", m.za());
        assertEquals("GFS: studentu ne stiže kod", m.naslov());
        assertTrue(m.telo().contains(s.getIndeks()), m.telo());
        assertTrue(m.telo().contains("studenti/" + s.getId()), m.telo());
        assertFalse(m.telo().contains("Test") || m.telo().contains("Studentić"), "ime ne sme u mejl: " + m.telo());
        assertFalse(m.telo().contains("ana@example.test"), "mejl studenta ne sme u mejl");
        assertFalse(m.naslov().contains(s.getIndeks()));
    }

    @Test
    void nepostojeciIndeksDobijaMejlBezLinka() {
        String indeks = nepostojeci();
        pomoc(indeks);
        MejlPoruka m = mejl.cekaj(1).getFirst();
        assertTrue(m.telo().contains(indeks), m.telo());
        assertFalse(m.telo().contains("studenti/"), m.telo());
    }

    @Test
    void drugiZahtevUSatuNemaNovogMejla() throws Exception {
        Student s = student("ana@example.test");
        pomoc(s.getIndeks());
        pomoc(s.getIndeks());
        mejl.cekaj(1);
        Thread.sleep(300);
        assertEquals(1, mejl.poslate().size());
    }

    @Test
    void limitMejlaPoVrstiIIndeksuPrepustaObavestenjeAliNeMejl() throws Exception {
        Student s = student("ana@example.test");
        String kljuc = "NE_STIZE_KOD:" + s.getIndeks();
        kljucevi.add(kljuc);
        pokusaji.zabelezi(VrstaPokusaja.MEJL_NASTAVNIKU, kljuc);
        pomoc(s.getIndeks());
        Thread.sleep(300);
        assertEquals(1, jdbc.queryForObject("select count(*) from obavestenja", Integer.class));
        assertTrue(mejl.poslate().isEmpty());
    }

    @Test
    void ukupniLimitMejlaJeDvadesetNaSat() throws Exception {
        for (int i = 0; i < 20; i++) {
            pokusaji.zabelezi(VrstaPokusaja.MEJL_NASTAVNIKU_UKUPNO, "ukupno");
        }
        pomoc(nepostojeci());
        Thread.sleep(300);
        assertEquals(1, jdbc.queryForObject("select count(*) from obavestenja", Integer.class));
        assertTrue(mejl.poslate().isEmpty());
    }

    @Test
    void dvadesetPrviMejlUSatuSeNeSalje() throws Exception {
        for (int i = 0; i < 21; i++) {
            pomoc(nepostojeci());
        }
        mejl.cekaj(20);
        Thread.sleep(300);
        assertEquals(20, mejl.poslate().size());
        assertEquals(21, jdbc.queryForObject("select count(*) from obavestenja", Integer.class));
        sat.pomeri(Duration.ofMinutes(61));
        pomoc(nepostojeci());
        mejl.cekaj(21);
    }

    @Test
    void bezSmtpaNemaMejlaNiBrojaca() throws Exception {
        mejl.dostupan(false);
        pomoc(nepostojeci());
        Thread.sleep(300);
        assertEquals(1, jdbc.queryForObject("select count(*) from obavestenja", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from pokusaji where vrsta = 'MEJL_NASTAVNIKU_UKUPNO'", Integer.class));
    }

    @Test
    void mejlIdePosleCommitaAZahtevGaNeCeka() {
        mejl.kasnjenje(Duration.ofSeconds(3));
        String indeks = nepostojeci();
        long t0 = System.nanoTime();
        pomoc(indeks);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 1500, "zahtev čeka mejl: " + ms + " ms");
        assertTrue(mejl.poslate().isEmpty(), "mejl ne sme da stigne pre kraja zahteva");
        mejl.cekaj(1, Duration.ofSeconds(10));
    }

    @Test
    void previseKodovaSaljeJedanMejlDnevnoPoStudentu() throws Exception {
        Student s = student("ana@example.test");
        servis.previseKodova(s.getId(), noviIp());
        servis.previseKodova(s.getId(), noviIp());
        MejlPoruka m = mejl.cekaj(1).getFirst();
        assertEquals("nastavnik@example.test", m.za());
        assertTrue(m.telo().contains(s.getIndeks()) && m.telo().contains("studenti/" + s.getId()), m.telo());
        assertFalse(m.naslov().contains(s.getIndeks()));
        Thread.sleep(300);
        assertEquals(1, mejl.poslate().size());
    }

    @Test
    void ipLimit429NeSaljeMejl() throws Exception {
        String ip = noviIp();
        for (int i = 0; i < 10; i++) {
            servis.neStizeKod(nepostojeci(), ip, Optional.empty());
        }
        mejl.cekaj(10);
        SystemException e = assertThrows(SystemException.class, () -> servis.neStizeKod(nepostojeci(), ip, Optional.empty()));
        assertEquals(429, e.getCode());
        Thread.sleep(300);
        assertEquals(10, mejl.poslate().size());
    }
}
