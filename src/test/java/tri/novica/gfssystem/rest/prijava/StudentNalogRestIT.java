package tri.novica.gfssystem.rest.prijava;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.mejl.MejlPoruka;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.PokusajiServis;
import tri.novica.gfssystem.service.prijava.StudentNalogServis;
import tri.novica.gfssystem.service.prijava.TestStudenti;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code /studenti/{id}/nalog} (nastavnik, ADMIN, spec 5.3) kroz ceo bezbednosni lanac nad pravom MySQL bazom, sa lažnim
 * mejlom: pregled naloga, kod sa kartice (rešava "Ne stiže mi kod"), opoziv uređaja, raskid Moodle veze.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LazniMejlServis.class)
@ExtendWith(OutputCaptureExtension.class)
class StudentNalogRestIT {

    static final Pattern KOD = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired MockMvc mvc;
    @Autowired LazniMejlServis mejl;
    @Autowired StudentRepository studentRepository;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired StudentNalogServis servis;
    @Autowired PokusajiServis pokusaji;
    @Autowired TokenGenerator tokenGenerator;
    @Autowired PlatformTransactionManager transakcije;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;
    RequestPostProcessor nastavnik;

    @BeforeEach
    void setUp() {
        mejl.ocisti();
        jdbc.update("delete from obavestenja");
        jdbc.update("delete from pokusaji where vrsta = 'KOD_SA_KARTICE'");
        studenti = new TestStudenti(studentRepository, jdbc);
        nastavnik = PrijavaTestUtil.kaoNastavnik(PrijavaTestUtil.nastavnickiKolacic(nastavnici));
    }

    @AfterEach
    void ocisti() {
        mejl.ocisti();
        studenti.obrisi();
        jdbc.update("delete from obavestenja");
        jdbc.update("delete from pokusaji where vrsta in ('KOD_SA_KARTICE', 'KOD_SLANJE_STUDENT')");
    }

    // ---------------------------------------------------------------- pomoćnici

    /** Uređaj studenta pravo u bazi; vraća token kolačića. {@code opozvan}/{@code istice} mogu biti {@code null}/podrazumevano. */
    String uredjaj(Student s, String nacin, LocalDateTime poslednjiUlaz, LocalDateTime opozvan) {
        String token = tokenGenerator.novi();
        LocalDateTime sada = LocalDateTime.now();
        jdbc.update("insert into studentski_uredjaji (student_id, token_hes, opis, nacin, prvi_ulaz, poslednji_ulaz, istice, opozvan) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                s.getId(), TokenHash.od(token), "Android · Chrome", nacin, Timestamp.valueOf(poslednjiUlaz.minusDays(2)),
                Timestamp.valueOf(poslednjiUlaz), Timestamp.valueOf(sada.plusDays(300)),
                opozvan == null ? null : Timestamp.valueOf(opozvan));
        return token;
    }

    void moodle(Student s, String subjekat, LocalDateTime poslednjiUlaz) {
        jdbc.update("insert into spoljni_identiteti (student_id, vrsta, izdavac, subjekat, kreirano, poslednji_ulaz) "
                        + "values (?, 'LTI', 'https://moodle.example.test', ?, ?, ?)",
                s.getId(), subjekat, Timestamp.valueOf(LocalDateTime.now().minusDays(3)),
                poslednjiUlaz == null ? null : Timestamp.valueOf(poslednjiUlaz));
    }

    int obavestenje(Student s, String vrsta) {
        return jdbc.update("insert into obavestenja (vrsta, student_id, podaci, kreirano) values (?, ?, '{}', ?)",
                vrsta, s == null ? null : s.getId(), Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)));
    }

    int otvorenaZa(Student s) {
        return jdbc.queryForObject("select count(*) from obavestenja where student_id = ? and reseno is null",
                Integer.class, s.getId());
    }

    int vazecihUredjaja(Student s) {
        return jdbc.queryForObject("select count(*) from studentski_uredjaji where student_id = ? and opozvan is null",
                Integer.class, s.getId());
    }

    static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sOriginom(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("Origin", PrijavaTestUtil.ORIGIN);
    }

    // ---------------------------------------------------------------- pristup

    @Test
    void bezNastavnickeSesijeJe401IStudentskiKolacicNijeDovoljan() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String token = uredjaj(s, "KOD", LocalDateTime.now(), null);
        String o = PrijavaTestUtil.ORIGIN;
        for (var b : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/studenti/{id}/nalog", s.getId()),
                post("/studenti/{id}/nalog/posalji-kod", s.getId()).header("Origin", o),
                post("/studenti/{id}/nalog/opozovi-uredjaje", s.getId()).header("Origin", o),
                delete("/studenti/{id}/nalog/moodle", s.getId()).header("Origin", o)}) {
            mvc.perform(b).andExpect(status().isUnauthorized());
        }
        // studentski kolačić nije nastavnička sesija: 401/403, nikad 2xx
        mvc.perform(get("/studenti/{id}/nalog", s.getId()).cookie(new Cookie(Kolacici.STUDENT, token)))
                .andExpect(status().is4xxClientError());
        assertEquals(1, vazecihUredjaja(s));
        assertTrue(mejl.poslate().isEmpty());
    }

    @Test
    void upisBezOriginaJeOdbijen() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik).header("Origin", "https://evil.example.test"))
                .andExpect(status().isForbidden());
        assertTrue(mejl.poslate().isEmpty());
    }

    // ---------------------------------------------------------------- GET

    @Test
    void pregledImaOblikUgovora() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        LocalDateTime sada = LocalDateTime.now();
        uredjaj(s, "KOD", sada.minusHours(5), null);
        uredjaj(s, "LTI", sada.minusMinutes(3), null);
        moodle(s, "sub-1", sada.minusMinutes(3));

        String telo = mvc.perform(get("/studenti/{id}/nalog", s.getId()).with(nastavnik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imaMejl").value(true))
                .andExpect(jsonPath("$.uredjaji", hasSize(2)))
                // najskorije korišćen prvi
                .andExpect(jsonPath("$.uredjaji[0].nacin").value("LTI"))
                .andExpect(jsonPath("$.uredjaji[1].nacin").value("KOD"))
                .andExpect(jsonPath("$.uredjaji[0].opis").value("Android · Chrome"))
                .andExpect(jsonPath("$.uredjaji[0].trenutni").value(false))
                .andExpect(jsonPath("$.uredjaji[0].id").isNumber())
                .andExpect(jsonPath("$.uredjaji[0].prviUlaz").isString())
                .andExpect(jsonPath("$.uredjaji[0].poslednjiUlaz").isString())
                .andExpect(jsonPath("$.moodle.povezan").value(true))
                .andExpect(jsonPath("$.moodle.poslednjiUlaz").isString())
                .andExpect(jsonPath("$.poslednjaPrijava").isString())
                .andReturn().getResponse().getContentAsString();
        assertFalse(telo.contains("ana@example.test"), "mejl studenta ne ide u odgovor");
        assertFalse(telo.contains("sub-1"), "Moodle subjekat ne ide u odgovor");
        assertFalse(telo.contains("tokenHes"), telo);
    }

    @Test
    void pregledStudentaBezNalogaImaPraznaStanja() throws Exception {
        Student s = studenti.napravi(null);
        mvc.perform(get("/studenti/{id}/nalog", s.getId()).with(nastavnik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imaMejl").value(false))
                .andExpect(jsonPath("$.uredjaji", hasSize(0)))
                .andExpect(jsonPath("$.moodle.povezan").value(false))
                .andExpect(jsonPath("$.moodle.poslednjiUlaz").value(nullValue()))
                .andExpect(jsonPath("$.poslednjaPrijava").value(nullValue()));
    }

    @Test
    void prazanMejlSePrikazujeKaoBezMejla() throws Exception {
        Student s = studenti.napravi("   ");
        mvc.perform(get("/studenti/{id}/nalog", s.getId()).with(nastavnik))
                .andExpect(jsonPath("$.imaMejl").value(false));
    }

    @Test
    void uredjajiSuSamoVazeciAliPoslednjaPrijavaPamtiIOpozvane() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        LocalDateTime sada = LocalDateTime.now();
        uredjaj(s, "KOD", sada.minusDays(4), null);
        LocalDateTime opozvanUlaz = sada.minusHours(1);
        uredjaj(s, "KOD", opozvanUlaz, sada.minusMinutes(30));
        mvc.perform(get("/studenti/{id}/nalog", s.getId()).with(nastavnik))
                .andExpect(jsonPath("$.uredjaji", hasSize(1)))
                .andExpect(jsonPath("$.uredjaji[0].poslednjiUlaz", startsWith(sada.minusDays(4).toString().substring(0, 16))))
                .andExpect(jsonPath("$.poslednjaPrijava", startsWith(opozvanUlaz.toString().substring(0, 16))));
    }

    @Test
    void tudjiUredjajiIVezeSeNeVide() throws Exception {
        Student a = studenti.napravi("a@example.test");
        Student b = studenti.napravi("b@example.test");
        uredjaj(b, "KOD", LocalDateTime.now(), null);
        moodle(b, "sub-b", LocalDateTime.now());
        mvc.perform(get("/studenti/{id}/nalog", a.getId()).with(nastavnik))
                .andExpect(jsonPath("$.uredjaji", hasSize(0)))
                .andExpect(jsonPath("$.moodle.povezan").value(false));
    }

    @Test
    void nepostojeciStudentJe404ZaSveAkcije() throws Exception {
        mvc.perform(get("/studenti/{id}/nalog", -5).with(nastavnik))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Ne postoji."));
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", -5).with(nastavnik))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Ne postoji."));
        mvc.perform(post("/studenti/{id}/nalog/opozovi-uredjaje", -5).with(nastavnik))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Ne postoji."));
        mvc.perform(delete("/studenti/{id}/nalog/moodle", -5).with(nastavnik))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Ne postoji."));
        mvc.perform(get("/studenti/abc/nalog").with(nastavnik)).andExpect(status().isBadRequest());
    }

    @Test
    void nepostojeciStudentJe404IKadSlanjeNijePodeseno() throws Exception {
        mejl.dostupan(false);
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", -5).with(nastavnik)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- posalji-kod

    @Test
    void posaljiKodSaljeKodNaMejlStudentaIResavaNjegovaObavestenja() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        Student drugi = studenti.napravi("drugi@example.test");
        obavestenje(s, "NE_STIZE_KOD");
        obavestenje(s, "PREVISE_KODOVA");
        obavestenje(drugi, "NE_STIZE_KOD");
        obavestenje(null, "NE_STIZE_KOD");

        mvc.perform(sOriginom(post("/studenti/{id}/nalog/posalji-kod", s.getId())).with(nastavnik))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        MejlPoruka m = mejl.cekaj(1).getFirst();
        assertEquals("ana@example.test", m.za());
        Matcher kod = KOD.matcher(m.telo());
        assertTrue(kod.find(), m.telo());
        assertEquals(1, jdbc.queryForObject("select count(*) from kodovi_za_prijavu where student_id = ? and iskoriscen is null and ponisten is null",
                Integer.class, s.getId()));
        // rešena su sva otvorena obaveštenja ovog studenta (i označena pročitanim), tuđa i ona bez studenta ne
        assertEquals(0, otvorenaZa(s));
        assertEquals(2, jdbc.queryForObject("select count(*) from obavestenja where student_id = ? and reseno is not null and procitano is not null",
                Integer.class, s.getId()));
        assertEquals(1, otvorenaZa(drugi));
        assertEquals(1, jdbc.queryForObject("select count(*) from obavestenja where student_id is null and reseno is null", Integer.class));
    }

    @Test
    void kodSaKarticeVaziZaPrijavuIPonistavaPrethodni() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik)).andExpect(status().isNoContent());
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik)).andExpect(status().isNoContent());
        assertEquals(2, mejl.cekaj(2).size());
        assertEquals(1, jdbc.queryForObject("select count(*) from kodovi_za_prijavu where student_id = ? and ponisten is null",
                Integer.class, s.getId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from kodovi_za_prijavu where student_id = ? and ponisten is not null",
                Integer.class, s.getId()));
    }

    @Test
    void studentBezMejlaJe409INeResavaObavestenja() throws Exception {
        Student s = studenti.napravi(null);
        obavestenje(s, "NE_STIZE_KOD");
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("Student nema mejl."));
        assertEquals(1, otvorenaZa(s));
        assertTrue(mejl.poslate().isEmpty());
    }

    @Test
    void bezPodesenogSmtpaJe503INeResavaObavestenja() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        obavestenje(s, "NE_STIZE_KOD");
        mejl.dostupan(false);
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("Slanje koda trenutno nije moguće. Javi se nastavniku."));
        assertEquals(1, otvorenaZa(s));
    }

    @Test
    void dvadesetPrviKodNaSatSaKarticeJe429() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        for (int i = 0; i < 20; i++) {
            pokusaji.zabelezi(VrstaPokusaja.KOD_SA_KARTICE, "ukupno");
        }
        obavestenje(s, "NE_STIZE_KOD");
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("Previše pokušaja. Pokušaj ponovo kasnije."));
        assertEquals(1, otvorenaZa(s));
        assertTrue(mejl.poslate().isEmpty());
    }

    @Test
    void karticaPreskaceLimitPoStudentuINeStvaraObavestenjePreviseKodova() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik)).andExpect(status().isNoContent());
        }
        assertEquals(5, mejl.cekaj(5).size());
        Thread.sleep(500);
        assertEquals(0, jdbc.queryForObject("select count(*) from obavestenja", Integer.class),
                "kod sa kartice ne objavljuje DostignutLimitKodova");
        assertEquals(0, jdbc.queryForObject("select count(*) from pokusaji where vrsta = 'KOD_SLANJE_STUDENT' and kljuc = ?",
                Integer.class, String.valueOf(s.getId())));
    }

    @Test
    void posaljiKodNeSmeUSpoljnojTransakciji() {
        Student s = studenti.napravi("ana@example.test");
        obavestenje(s, "NE_STIZE_KOD");
        assertThrows(IllegalTransactionStateException.class,
                () -> new TransactionTemplate(transakcije).executeWithoutResult(tx -> servis.posaljiKod(s.getId())));
        assertTrue(mejl.poslate().isEmpty());
        assertEquals(1, otvorenaZa(s));
    }

    @Test
    void indeksVanBeleListeSeLogujeBezIndeksa(CapturedOutput izlaz) throws Exception {
        Student s = studenti.napravi("ĐĐ-" + TestStudenti.noviIndeks().substring(3, 8) + "/2025", 2025, "ana@example.test");
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik)).andExpect(status().isNoContent());
        assertTrue(izlaz.getAll().contains("mimo bele liste"), "upozorenje o indeksu koji se ne može koristiti za prijavu");
        assertFalse(izlaz.getAll().contains(s.getIndeks()), "indeks se ne loguje");
        assertFalse(izlaz.getAll().contains("ana@example.test"));
    }

    // ---------------------------------------------------------------- opozovi-uredjaje

    @Test
    void opozivUredjajaVracaBrojVazecihIDaljeNeVazi() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        Student tudji = studenti.napravi("tudji@example.test");
        LocalDateTime sada = LocalDateTime.now();
        String t1 = uredjaj(s, "KOD", sada.minusHours(1), null);
        String t2 = uredjaj(s, "LTI", sada.minusHours(2), null);
        uredjaj(s, "KOD", sada.minusDays(3), sada.minusDays(2));
        String tuđ = uredjaj(tudji, "KOD", sada, null);
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, t1))).andExpect(status().isOk());

        mvc.perform(sOriginom(post("/studenti/{id}/nalog/opozovi-uredjaje", s.getId())).with(nastavnik))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"opozvano\":2}", true));

        assertEquals(0, vazecihUredjaja(s));
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, t1))).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, t2))).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, tuđ))).andExpect(status().isOk());
        assertEquals(1, vazecihUredjaja(tudji));

        // ponovljeno: nema šta da se opozove
        mvc.perform(post("/studenti/{id}/nalog/opozovi-uredjaje", s.getId()).with(nastavnik))
                .andExpect(status().isOk()).andExpect(content().json("{\"opozvano\":0}", true));
        mvc.perform(get("/studenti/{id}/nalog", s.getId()).with(nastavnik)).andExpect(jsonPath("$.uredjaji", hasSize(0)));
    }

    // ---------------------------------------------------------------- moodle

    @Test
    void raskidMoodleVezeBrisePoveznicuIOpozivaLtiUredjaje() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        Student tudji = studenti.napravi("tudji@example.test");
        LocalDateTime sada = LocalDateTime.now();
        moodle(s, "sub-1", sada);
        moodle(tudji, "sub-2", sada);
        String lti = uredjaj(s, "LTI", sada, null);
        String kod = uredjaj(s, "KOD", sada, null);
        String tudjiLti = uredjaj(tudji, "LTI", sada, null);

        mvc.perform(sOriginom(delete("/studenti/{id}/nalog/moodle", s.getId())).with(nastavnik))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertEquals(0, jdbc.queryForObject("select count(*) from spoljni_identiteti where student_id = ?", Integer.class, s.getId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from spoljni_identiteti where student_id = ?", Integer.class, tudji.getId()));
        // uređaj otvoren kroz Moodle ne preživljava raskid veze; uređaj sa koda ostaje
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, lti))).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, kod)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.moodlePovezan").value(false));
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, tudjiLti))).andExpect(status().isOk());
        mvc.perform(get("/studenti/{id}/nalog", s.getId()).with(nastavnik))
                .andExpect(jsonPath("$.moodle.povezan").value(false))
                .andExpect(jsonPath("$.moodle.poslednjiUlaz").value(nullValue()))
                .andExpect(jsonPath("$.uredjaji", hasSize(1)));

        // ponovljeno je i dalje 204
        mvc.perform(delete("/studenti/{id}/nalog/moodle", s.getId()).with(nastavnik)).andExpect(status().isNoContent());
        // posle raskida isti Moodle nalog može da se poveže sa drugim studentom (jedinstvenost je oslobođena)
        moodle(studenti.napravi("treci@example.test"), "sub-1", sada);
    }

    @Test
    void odgovoriNeNoseTokenIliKodUTelu() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        mvc.perform(post("/studenti/{id}/nalog/posalji-kod", s.getId()).with(nastavnik))
                .andExpect(content().string(""));
        String kod = KOD.matcher(mejl.cekaj(1).getFirst().telo()).results().findFirst().orElseThrow().group(1);
        String pregled = mvc.perform(get("/studenti/{id}/nalog", s.getId()).with(nastavnik)).andReturn().getResponse().getContentAsString();
        assertFalse(pregled.contains(kod));
        assertFalse(pregled.contains("kod_hes"));
    }
}
