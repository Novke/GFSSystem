package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tri.novica.gfssystem.dto.prijava.StudentJaInfo;
import tri.novica.gfssystem.dto.prijava.UredjajInfo;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.StudentskiUredjaj;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.prijava.StudentskiUredjajRepository;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Uređaji studenta (spec 2.2, 5.1): kolačić {@code gfs_student} 365 dana, produženje korišćenjem, odjava. */
@SpringBootTest
@Import({PrijavaSatConfig.class, LazniMejlServis.class})
class StudentskaPrijavaIT {

    static final String ANDROID_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/129.0.0.0 Mobile Safari/537.36";

    @Autowired StudentskaPrijava prijava;
    @Autowired StudentskiUredjajRepository uredjaji;
    @Autowired StudentRepository studentRepository;
    @Autowired PomerljivSat sat;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;

    @BeforeEach
    void setUp() {
        sat.postavi(Instant.now());
        studenti = new TestStudenti(studentRepository, jdbc);
    }

    @AfterEach
    void ocisti() {
        studenti.obrisi();
    }

    /** Prijavljuje studenta i vraća token iz {@code Set-Cookie}. */
    String prijavi(Student s, NacinPrijave nacin, boolean https) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("User-Agent", ANDROID_UA);
        req.setSecure(https);
        MockHttpServletResponse res = new MockHttpServletResponse();
        prijava.prijavi(s.getId(), nacin, req, res);
        String h = res.getHeader("Set-Cookie");
        assertNotNull(h);
        assertTrue(h.startsWith("gfs_student="), h);
        return h.substring("gfs_student=".length(), h.indexOf(';'));
    }

    StudentskiUredjaj uredjaj(String token) {
        return uredjaji.findByTokenHes(TokenHash.od(token)).orElseThrow();
    }

    @Test
    void prijavaPraviUredjajIKolacic() {
        Student s = studenti.napravi("pera@example.test");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("User-Agent", ANDROID_UA);
        MockHttpServletResponse res = new MockHttpServletResponse();
        prijava.prijavi(s.getId(), NacinPrijave.KOD, req, res);

        String h = res.getHeader("Set-Cookie");
        assertTrue(h.contains("Max-Age=31536000"), h);
        assertTrue(h.contains("SameSite=Lax"), h);
        assertTrue(h.contains("HttpOnly"), h);
        assertTrue(h.contains("Path=/"), h);
        assertFalse(h.contains("Secure"), h);
        String token = h.substring("gfs_student=".length(), h.indexOf(';'));
        assertTrue(TokenGenerator.FORMAT.matcher(token).matches(), token);

        StudentskiUredjaj u = uredjaj(token);
        assertEquals(s.getId(), u.getStudent().getId());
        assertEquals("Android · Chrome", u.getOpis());
        assertEquals(NacinPrijave.KOD, u.getNacin());
        assertNull(u.getOpozvan());
        long razlika = Duration.between(LocalDateTime.now(clock).plusDays(365), u.getIstice()).abs().toMillis();
        assertTrue(razlika < 1000, "istice odstupa " + razlika + " ms");
        // u bazi je samo heš
        assertEquals(0, jdbc.queryForObject("select count(*) from studentski_uredjaji where token_hes = ?",
                Integer.class, token));
    }

    @Test
    void httpsDajeSecure() {
        Student s = studenti.napravi("pera@example.test");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(true);
        MockHttpServletResponse res = new MockHttpServletResponse();
        prijava.prijavi(s.getId(), NacinPrijave.LTI, req, res);
        assertTrue(res.getHeader("Set-Cookie").contains("Secure"));
    }

    @Test
    void potvrdiVazeciOpozvanIstekaoINeispravan() {
        Student s = studenti.napravi("pera@example.test");
        String token = prijavi(s, NacinPrijave.KOD, false);
        Optional<StudentskiUredjaj> u = prijava.potvrdi(token);
        assertTrue(u.isPresent());
        assertEquals(s.getId(), u.get().getStudent().getId());

        assertTrue(prijava.potvrdi(null).isEmpty());
        assertTrue(prijava.potvrdi("kratak").isEmpty());
        assertTrue(prijava.potvrdi(new TokenGenerator().novi()).isEmpty());

        String drugi = prijavi(s, NacinPrijave.KOD, false);
        prijava.odjavi(drugi);
        assertTrue(prijava.potvrdi(drugi).isEmpty());
        assertTrue(prijava.potvrdi(token).isPresent());

        sat.pomeri(Duration.ofDays(366));
        assertTrue(prijava.potvrdi(token).isEmpty());
    }

    @Test
    void koriscenjeProduzavaNajviseJednomNa10Minuta() {
        Student s = studenti.napravi("pera@example.test");
        String token = prijavi(s, NacinPrijave.KOD, false);
        LocalDateTime istice0 = uredjaj(token).getIstice();

        sat.pomeri(Duration.ofMinutes(5));
        prijava.potvrdi(token);
        assertEquals(istice0, uredjaj(token).getIstice());

        sat.pomeri(Duration.ofMinutes(6));
        prijava.potvrdi(token);
        StudentskiUredjaj u = uredjaj(token);
        assertTrue(u.getIstice().isAfter(istice0.plusMinutes(10)), u.getIstice() + " vs " + istice0);
        long razlika = Duration.between(LocalDateTime.now(clock), u.getPoslednjiUlaz()).abs().toMillis();
        assertTrue(razlika < 1000);

        // 360 dana kasnije uređaj i dalje važi jer je korišćen
        sat.pomeri(Duration.ofDays(360));
        assertTrue(prijava.potvrdi(token).isPresent());
    }

    @Test
    void odjaviSveOpozivaSveUredjajeStudenta() {
        Student s = studenti.napravi("pera@example.test");
        Student drugi = studenti.napravi("mika@example.test");
        String a = prijavi(s, NacinPrijave.KOD, false);
        String b = prijavi(s, NacinPrijave.LTI, false);
        String tudji = prijavi(drugi, NacinPrijave.KOD, false);

        assertEquals(2, prijava.odjaviSve(s.getId()));
        // bulk update ne osvežava učitane entitete: čita se ponovo iz baze
        assertNotNull(uredjaj(a).getOpozvan());
        assertNotNull(uredjaj(b).getOpozvan());
        assertTrue(prijava.potvrdi(a).isEmpty());
        assertTrue(prijava.potvrdi(b).isEmpty());
        assertTrue(prijava.potvrdi(tudji).isPresent());
        assertEquals(0, prijava.odjaviSve(s.getId()));
    }

    @Test
    void odjaviUredjajPoIdu() {
        Student s = studenti.napravi("pera@example.test");
        String a = prijavi(s, NacinPrijave.KOD, false);
        String b = prijavi(s, NacinPrijave.KOD, false);
        prijava.odjaviUredjaj(uredjaj(a).getId());
        assertTrue(prijava.potvrdi(a).isEmpty());
        assertTrue(prijava.potvrdi(b).isPresent());
        prijava.odjaviUredjaj(-1L);   // nepoznat: ćutke
        prijava.odjavi("nepostojeci-token");
        prijava.odjavi(null);
    }

    @Test
    void spisakUredjajaOznacavaTrenutni() {
        Student s = studenti.napravi("pera@example.test");
        String a = prijavi(s, NacinPrijave.KOD, false);
        sat.pomeri(Duration.ofMinutes(1));
        String b = prijavi(s, NacinPrijave.LTI, false);
        String opozvan = prijavi(s, NacinPrijave.KOD, false);
        prijava.odjavi(opozvan);

        Long idB = uredjaj(b).getId();
        List<UredjajInfo> lista = prijava.uredjaji(s.getId(), idB);
        assertEquals(2, lista.size());
        assertEquals(idB, lista.getFirst().id());   // najskorije korišćen prvi
        assertTrue(lista.getFirst().trenutni());
        assertEquals(NacinPrijave.LTI, lista.getFirst().nacin());
        assertFalse(lista.get(1).trenutni());
        assertEquals("Android · Chrome", lista.get(1).opis());
        assertNotNull(lista.get(1).prviUlaz());
        assertNotNull(lista.get(1).poslednjiUlaz());
        assertEquals(uredjaj(a).getId(), lista.get(1).id());
    }

    @Test
    void jaVracaPodatkeNaloga() {
        Student s = studenti.napravi("pera.peric@example.test");
        String a = prijavi(s, NacinPrijave.KOD, false);
        StudentJaInfo ja = prijava.ja(s.getId(), uredjaj(a).getId());
        assertEquals(s.getIndeks(), ja.indeks());
        assertEquals("Test", ja.ime());
        assertEquals("Studentić", ja.prezime());
        assertEquals("p***@example.test", ja.maskaMejla());
        assertEquals(1, ja.uredjaji().size());
        assertTrue(ja.uredjaji().getFirst().trenutni());
        assertFalse(ja.moodlePovezan());

        Student bezMejla = studenti.napravi(null);
        assertNull(prijava.ja(bezMejla.getId(), null).maskaMejla());
    }
}
