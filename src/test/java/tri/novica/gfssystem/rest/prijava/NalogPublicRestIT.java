package tri.novica.gfssystem.rest.prijava;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.mejl.MejlPoruka;
import tri.novica.gfssystem.service.prijava.MaskaMejla;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.PristupBravama;
import tri.novica.gfssystem.service.prijava.TestStudenti;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code /public/nalog} kroz ceo bezbednosni lanac (MockMvc sa filterima) nad pravom MySQL bazom, sa lažnim mejlom
 * (spec 5.1, 5.2, 2.2). Svaki test ima svoj IP (IPv6 iz 2001:db8::/32) i svoje studente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LazniMejlServis.class)
class NalogPublicRestIT {

    static final Pattern KOD = Pattern.compile("\\b(\\d{6})\\b");
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PORUKA = "Ako nalog postoji, kod je poslat.";

    @Autowired MockMvc mvc;
    @Autowired LazniMejlServis mejl;
    @Autowired MaskaMejla maska;
    @Autowired StudentRepository studentRepository;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired JdbcTemplate jdbc;
    @Autowired tri.novica.gfssystem.service.prijava.KodServis kodServis;
    @Autowired javax.sql.DataSource dataSource;

    TestStudenti studenti;
    final List<String> ipovi = new ArrayList<>();
    String ip;

    @BeforeEach
    void setUp() {
        mejl.ocisti();
        studenti = new TestStudenti(studentRepository, jdbc);
        ip = noviIp();
    }

    @AfterEach
    void ocisti() {
        mejl.ocisti();
        studenti.obrisi();
        jdbc.update("delete from obavestenja");
        jdbc.update("delete from pokusaji where vrsta = 'POMOC_INDEKS'");
        for (String i : ipovi) {
            jdbc.update("delete from pokusaji where kljuc = ?", i);
        }
    }

    String noviIp() {
        String i = "2001:db8::" + UUID.randomUUID().toString().substring(0, 4);
        ipovi.add(i);
        return i;
    }

    RequestPostProcessor saIp() {
        return req -> {
            req.setRemoteAddr(ip);
            return req;
        };
    }

    MockHttpServletRequestBuilder javniPost(String putanja, String telo) {
        return post(putanja).with(saIp()).header(HttpHeaders.ORIGIN, PrijavaTestUtil.ORIGIN)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                        + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
                .contentType(MediaType.APPLICATION_JSON).content(telo);
    }

    MvcResult kod(String indeks) throws Exception {
        return mvc.perform(javniPost("/public/nalog/kod", "{\"indeks\":\"" + indeks + "\"}")).andReturn();
    }

    static String kodIz(MejlPoruka p) {
        Matcher m = KOD.matcher(p.telo());
        assertTrue(m.find());
        return m.group(1);
    }

    /** Ceo tok kod -> prijava; vraća vrednost kolačića {@code gfs_student}. */
    String prijaviSe(Student s) throws Exception {
        int pre = mejl.poslate().size();
        assertEquals(200, kod(s.getIndeks()).getResponse().getStatus());
        String k = kodIz(mejl.cekaj(pre + 1).get(pre));
        MockHttpServletResponse r = mvc.perform(javniPost("/public/nalog/prijava",
                        "{\"indeks\":\"" + s.getIndeks() + "\",\"kod\":\"" + k + "\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        return vrednost(postavljen(r, Kolacici.STUDENT));
    }

    /** {@code Set-Cookie} koji postavlja (ne briše) kolačić datog imena. */
    static String postavljen(MockHttpServletResponse r, String ime) {
        List<String> h = r.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith(ime + "=") && !c.contains("Max-Age=0")).toList();
        assertEquals(1, h.size(), "Set-Cookie: " + r.getHeaders(HttpHeaders.SET_COOKIE));
        return h.getFirst();
    }

    static boolean obrisan(MockHttpServletResponse r, String ime) {
        return r.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(c -> c.startsWith(ime + "=;") && c.contains("Max-Age=0"));
    }

    static String vrednost(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    static Cookie student(String token) {
        return new Cookie(Kolacici.STUDENT, token);
    }

    @Test
    void ceoTokKodPrijavaJaOdjava() throws Exception {
        Student s = studenti.napravi("pera.peric@example.test");
        MvcResult r = kod(s.getIndeks());
        assertEquals(200, r.getResponse().getStatus());
        JsonNode o = JSON.readTree(r.getResponse().getContentAsString());
        assertEquals(PORUKA, o.get("poruka").asString());
        assertEquals("p***@example.test", o.get("maska").asString());
        assertEquals(Set.of("poruka", "maska"), polja(o));

        String k = kodIz(mejl.cekaj(1).getFirst());
        MockHttpServletResponse p = mvc.perform(javniPost("/public/nalog/prijava",
                        "{\"indeks\":\"" + s.getIndeks().toLowerCase() + "\",\"kod\":\"" + k + "\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        String setCookie = postavljen(p, Kolacici.STUDENT);
        assertTrue(setCookie.contains("Max-Age=31536000"), setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);
        String token = vrednost(setCookie);
        assertTrue(TokenGenerator.FORMAT.matcher(token).matches());

        mvc.perform(get("/public/nalog/ja").cookie(student(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indeks").value(s.getIndeks()))
                .andExpect(jsonPath("$.ime").value("Test"))
                .andExpect(jsonPath("$.prezime").value("Studentić"))
                .andExpect(jsonPath("$.maskaMejla").value("p***@example.test"))
                .andExpect(jsonPath("$.moodlePovezan").value(false))
                .andExpect(jsonPath("$.uredjaji", hasSize(1)))
                .andExpect(jsonPath("$.uredjaji[0].trenutni").value(true))
                .andExpect(jsonPath("$.uredjaji[0].nacin").value("KOD"))
                .andExpect(jsonPath("$.uredjaji[0].opis").value("iPhone · Safari"))
                .andExpect(content().string(not(containsString("pera.peric@"))))
                .andExpect(content().string(not(containsString("tokenHes"))));
        mvc.perform(get("/public/nalog/stanje").cookie(student(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prijavljen").value(true));

        MockHttpServletResponse odj = mvc.perform(javniPost("/public/nalog/odjava", "").cookie(student(token)))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertTrue(obrisan(odj, Kolacici.STUDENT), odj.getHeaders(HttpHeaders.SET_COOKIE).toString());

        MockHttpServletResponse posle = mvc.perform(get("/public/nalog/ja").cookie(student(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("Potrebna je prijava."))
                .andReturn().getResponse();
        assertTrue(obrisan(posle, Kolacici.STUDENT), "nevažeći kolačić se briše");
        mvc.perform(get("/public/nalog/stanje").cookie(student(token)))
                .andExpect(jsonPath("$.prijavljen").value(false));
    }

    static Set<String> polja(JsonNode o) {
        return o.propertyNames().stream().collect(Collectors.toSet());
    }

    /** Brava po indeksu ({@code GET_LOCK}) se otpušta i kad zahtev ide kroz MVC (open-in-view drži istu vezu). */
    @Test
    void bravaPoIndeksuSeOtpustaKrozHttp() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        assertEquals(200, kod(s.getIndeks()).getResponse().getStatus());
        assertEquals(200, kod(TestStudenti.noviIndeks()).getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from performance_schema.metadata_locks
                where OBJECT_TYPE = 'USER LEVEL LOCK' and OBJECT_NAME like 'gfs-kod:%'""", Integer.class));
    }

    @Test
    void bezKolacica() throws Exception {
        mvc.perform(get("/public/nalog/stanje"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"prijavljen\":false}"));
        mvc.perform(get("/public/nalog/ja"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("Potrebna je prijava."));
        mvc.perform(javniPost("/public/nalog/odjava", "")).andExpect(status().isUnauthorized());
        mvc.perform(javniPost("/public/nalog/odjava-svuda", "")).andExpect(status().isUnauthorized());
    }

    @Test
    void nepostojeciIBezMejlaDajuIstoTeloKaoPostojeci() throws Exception {
        Student sa = studenti.napravi("ana@example.test");
        Student bez = studenti.napravi(null);
        String nema = TestStudenti.noviIndeks();
        for (String indeks : List.of(sa.getIndeks(), bez.getIndeks(), nema)) {
            MvcResult r = kod(indeks);
            assertEquals(200, r.getResponse().getStatus());
            JsonNode o = JSON.readTree(r.getResponse().getContentAsString());
            assertEquals(Set.of("poruka", "maska"), polja(o));
            assertEquals(PORUKA, o.get("poruka").asString());
            assertTrue(o.get("maska").asString().matches("^[a-z]\\*\\*\\*@[a-z.]+$"), o.toString());
        }
        assertEquals(maska.lazna(nema), JSON.readTree(kod(nema).getResponse().getContentAsString())
                .get("maska").asString());
    }

    @Test
    void pogresanKodINepostojeciIndeksDajuIsti401() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        kod(s.getIndeks());
        String k = kodIz(mejl.cekaj(1).getFirst());
        String pogresan = k.equals("000000") ? "111111" : "000000";
        for (String telo : List.of("{\"indeks\":\"" + s.getIndeks() + "\",\"kod\":\"" + pogresan + "\"}",
                "{\"indeks\":\"" + TestStudenti.noviIndeks() + "\",\"kod\":\"123456\"}")) {
            MockHttpServletResponse r = mvc.perform(javniPost("/public/nalog/prijava", telo))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.reason").value("Kod nije ispravan ili je istekao."))
                    .andReturn().getResponse();
            assertTrue(r.getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
        }
    }

    @Test
    void validacija() throws Exception {
        for (String telo : List.of("{}", "{\"indeks\":\"  \"}", "{\"indeks\":\"" + "A".repeat(31) + "\"}")) {
            mvc.perform(javniPost("/public/nalog/kod", telo)).andExpect(status().isBadRequest());
        }
        for (String kod : List.of("12345", "1234567", "abcdef", " 12345", "")) {
            mvc.perform(javniPost("/public/nalog/prijava", "{\"indeks\":\"GD1\",\"kod\":\"" + kod + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(javniPost("/public/nalog/prijava", "{\"kod\":\"123456\"}")).andExpect(status().isBadRequest());
        assertEquals(List.of(), mejl.poslate());
    }

    /**
     * Review focus 1: mejl ide posle commit-a u drugoj niti, pa odgovor ne čeka SMTP. Lažni mejl kasni 3 s; oba odgovora
     * moraju stići pre nego što je mejl uopšte poslat (dokaz asinhronosti nezavisan od opterećenja mašine) i za manje od
     * 1 s (trećina kašnjenja mejla; brief traži 300 ms, ali ova mašina radi pod opterećenjem ~12 na 4 jezgra).
     */
    @Test
    void odgovorNeCekaMejl() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String nema = TestStudenti.noviIndeks();
        // zagrevanje obe grane (prvi zahtev nosi JIT, keš upita, prvi HMAC ključ)
        kod(s.getIndeks());
        kod(nema);
        mejl.cekaj(1, Duration.ofSeconds(10));
        mejl.ocisti();
        mejl.kasnjenje(Duration.ofSeconds(3));

        long t0 = System.nanoTime();
        MvcResult postojeci = kod(s.getIndeks());
        long postojeciMs = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(List.of(), mejl.poslate(), "odgovor je stigao tek pošto je mejl poslat");
        long t1 = System.nanoTime();
        MvcResult nepostojeci = kod(nema);
        long nepostojeciMs = (System.nanoTime() - t1) / 1_000_000;

        assertEquals(200, postojeci.getResponse().getStatus());
        assertEquals(200, nepostojeci.getResponse().getStatus());
        System.out.println("Trajanje POST /public/nalog/kod: postojeći " + postojeciMs + " ms, nepostojeći "
                + nepostojeciMs + " ms (mejl kasni 3000 ms)");
        assertTrue(postojeciMs < 1000, "postojeći: " + postojeciMs + " ms");
        assertTrue(nepostojeciMs < 1000, "nepostojeći: " + nepostojeciMs + " ms");
        // mejl ipak stiže, posle odgovora
        assertEquals("ana@example.test", mejl.cekaj(1, Duration.ofSeconds(10)).getFirst().za());
    }

    @Test
    void bezSmtpaJe503IstoZaSve() throws Exception {
        mejl.dostupan(false);
        Student s = studenti.napravi("ana@example.test");
        for (String indeks : List.of(s.getIndeks(), TestStudenti.noviIndeks())) {
            mvc.perform(javniPost("/public/nalog/kod", "{\"indeks\":\"" + indeks + "\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.reason").value("Slanje koda trenutno nije moguće. Javi se nastavniku."));
        }
    }

    @Test
    void odjavaSvudaOpozivaSveUredjaje() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String a = prijaviSe(s);
        String b = prijaviSe(s);
        mvc.perform(get("/public/nalog/ja").cookie(student(a)))
                .andExpect(jsonPath("$.uredjaji", hasSize(2)));

        MockHttpServletResponse r = mvc.perform(javniPost("/public/nalog/odjava-svuda", "").cookie(student(b)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"odjavljeno\":2}"))
                .andReturn().getResponse();
        assertTrue(obrisan(r, Kolacici.STUDENT));
        mvc.perform(get("/public/nalog/ja").cookie(student(a))).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/nalog/ja").cookie(student(b))).andExpect(status().isUnauthorized());
    }

    @Test
    void novaPrijavaIstogPregledacaOdjavljujeStariUredjaj() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String stari = prijaviSe(s);
        kod(s.getIndeks());
        String k = kodIz(mejl.cekaj(2).get(1));
        String novi = vrednost(postavljen(mvc.perform(javniPost("/public/nalog/prijava",
                        "{\"indeks\":\"" + s.getIndeks() + "\",\"kod\":\"" + k + "\"}").cookie(student(stari)))
                .andExpect(status().isNoContent()).andReturn().getResponse(), Kolacici.STUDENT));
        mvc.perform(get("/public/nalog/ja").cookie(student(stari))).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/nalog/ja").cookie(student(novi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uredjaji", hasSize(1)));
    }

    /** Cookie tossing (spec 2.4): sestrinski poddomen podmetne {@code gfs_student} za {@code .trif.rs}. */
    @Test
    void podmetnutStudentskiKolacicNeObaraPravi() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String pravi = prijaviSe(s);
        Cookie podmetnut = student(new TokenGenerator().novi());
        MockHttpServletResponse r = mvc.perform(get("/public/nalog/ja").cookie(podmetnut, student(pravi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indeks").value(s.getIndeks()))
                .andReturn().getResponse();
        assertFalse(obrisan(r, Kolacici.STUDENT), "važeći kolačić se ne briše zbog podmetnutog");

        MockHttpServletResponse oba = mvc.perform(get("/public/nalog/stanje")
                        .cookie(podmetnut, student(new TokenGenerator().novi())))
                .andExpect(jsonPath("$.prijavljen").value(false))
                .andReturn().getResponse();
        assertTrue(obrisan(oba, Kolacici.STUDENT), "nijedan ne važi: kolačić se briše");
    }

    /**
     * Review B7 #4: filter proba najviše 5 istoimenih vrednosti. Kad ih ima više, pravi je možda bio šesti, pa se kolačić
     * ne briše (brisanje bi odjavilo studenta zbog podmetnutih); sa tačno 5 nevažećih se briše.
     */
    @Test
    void previseIstoimenihVrednostiNeBriseKolacic() throws Exception {
        Cookie[] sest = new Cookie[6];
        for (int i = 0; i < sest.length; i++) sest[i] = student(new TokenGenerator().novi());
        MockHttpServletResponse r = mvc.perform(get("/public/nalog/stanje").cookie(sest))
                .andExpect(jsonPath("$.prijavljen").value(false)).andReturn().getResponse();
        assertFalse(obrisan(r, Kolacici.STUDENT), r.getHeaders(HttpHeaders.SET_COOKIE).toString());

        Cookie[] pet = java.util.Arrays.copyOf(sest, 5);
        MockHttpServletResponse r5 = mvc.perform(get("/public/nalog/stanje").cookie(pet))
                .andReturn().getResponse();
        assertTrue(obrisan(r5, Kolacici.STUDENT));

        Cookie[] nastavnicki = new Cookie[6];
        for (int i = 0; i < nastavnicki.length; i++) {
            nastavnicki[i] = new Cookie(Kolacici.NASTAVNIK, new TokenGenerator().novi());
        }
        MockHttpServletResponse rn = mvc.perform(get("/auth/ja").cookie(nastavnicki)).andReturn().getResponse();
        assertFalse(obrisan(rn, Kolacici.NASTAVNIK));
    }

    @Test
    void podmetnutNastavnickiKolacicNeObaraPravi() throws Exception {
        String pravi = PrijavaTestUtil.nastavnickiKolacic(nastavnici).substring(Kolacici.NASTAVNIK.length() + 1);
        Cookie podmetnut = new Cookie(Kolacici.NASTAVNIK, new TokenGenerator().novi());
        MockHttpServletResponse r = mvc.perform(get("/auth/ja")
                        .cookie(podmetnut, new Cookie(Kolacici.NASTAVNIK, pravi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uloga").value("NASTAVNIK"))
                .andReturn().getResponse();
        assertFalse(obrisan(r, Kolacici.NASTAVNIK));
        nastavnici.odjavi(pravi);
    }

    @Test
    void nastavnikImaPrednostANevazeciNastavnickiPustaStudenta() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String st = prijaviSe(s);
        String na = PrijavaTestUtil.nastavnickiKolacic(nastavnici).substring(Kolacici.NASTAVNIK.length() + 1);
        // oba važe: nastavnik (spec 2.2), pa je studentska putanja 403
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.NASTAVNIK, na), student(st)))
                .andExpect(status().isForbidden());
        nastavnici.odjavi(na);
        // nastavnički više ne važi: briše se, a student je prijavljen
        MockHttpServletResponse r = mvc.perform(get("/public/nalog/ja")
                        .cookie(new Cookie(Kolacici.NASTAVNIK, na), student(st)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertTrue(obrisan(r, Kolacici.NASTAVNIK));
        assertFalse(obrisan(r, Kolacici.STUDENT));
    }

    @Test
    void studentNaNastavnickojPutanjiJe403() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String st = prijaviSe(s);
        mvc.perform(get("/studenti").cookie(student(st)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("Nemaš pristup."));
        mvc.perform(get("/auth/ja").cookie(student(st))).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- POST /public/nalog/pomoc (B8)

    MvcResult pomoc(String indeks) throws Exception {
        return pomoc(indeks, null);
    }

    MvcResult pomoc(String indeks, String studentToken) throws Exception {
        MockHttpServletRequestBuilder b = javniPost("/public/nalog/pomoc", "{\"indeks\":\"" + indeks + "\"}");
        if (studentToken != null) {
            b.cookie(student(studentToken));
        }
        return mvc.perform(b).andReturn();
    }

    int brojObavestenja() {
        return jdbc.queryForObject("select count(*) from obavestenja", Integer.class);
    }

    @Test
    void pomocDajeIstoTeloZaPostojecegBezMejlaINepostojeceg() throws Exception {
        jdbc.update("delete from obavestenja");
        Student sa = studenti.napravi("ana@example.test");
        Student bez = studenti.napravi(null);
        for (String indeks : List.of(sa.getIndeks(), bez.getIndeks(), TestStudenti.noviIndeks())) {
            MvcResult r = pomoc(indeks);
            assertEquals(200, r.getResponse().getStatus(), indeks);
            JsonNode o = JSON.readTree(r.getResponse().getContentAsString());
            assertEquals(Set.of("poruka"), polja(o));
            assertEquals("Javili smo nastavniku.", o.get("poruka").asString());
            assertTrue(r.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
        }
        assertEquals(3, brojObavestenja());
    }

    @Test
    void pomocTihiLimitPoIndeksuDajeIstiOdgovorBezNovogObavestenja() throws Exception {
        jdbc.update("delete from obavestenja");
        Student s = studenti.napravi("ana@example.test");
        String prvi = pomoc(s.getIndeks()).getResponse().getContentAsString();
        String drugi = pomoc(s.getIndeks()).getResponse().getContentAsString();
        String treci = pomoc(s.getIndeks().toLowerCase()).getResponse().getContentAsString();
        assertEquals(prvi, drugi);
        assertEquals(prvi, treci);
        assertEquals(1, brojObavestenja());
    }

    @Test
    void pomocJedanaestiZahtevSaIstogIpaJe429() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, pomoc(TestStudenti.noviIndeks()).getResponse().getStatus());
        }
        mvc.perform(javniPost("/public/nalog/pomoc", "{\"indeks\":\"" + TestStudenti.noviIndeks() + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("Previše pokušaja. Pokušaj ponovo kasnije."));
        // drugi IP prolazi
        ip = noviIp();
        assertEquals(200, pomoc(TestStudenti.noviIndeks()).getResponse().getStatus());
    }

    @Test
    void pomocPrepoznajeZapamcenUredjajIzKolacica() throws Exception {
        jdbc.update("delete from obavestenja");
        Student s = studenti.napravi("ana@example.test");
        String token = prijaviSe(s);
        assertEquals(200, pomoc(s.getIndeks(), token).getResponse().getStatus());
        String podaci = jdbc.queryForObject("select podaci from obavestenja where student_id = ?", String.class, s.getId());
        assertEquals("ZAPAMCEN", JSON.readTree(podaci).get("uredjaj").asString());
        assertFalse(podaci.contains(token));
    }

    @Test
    void pomocUredjajBezKolacicaIliSTudjimJeNepoznat() throws Exception {
        jdbc.update("delete from obavestenja");
        Student s = studenti.napravi("ana@example.test");
        Student drugi = studenti.napravi("pera@example.test");
        String tudj = prijaviSe(drugi);
        pomoc(s.getIndeks(), tudj);
        String podaci = jdbc.queryForObject("select podaci from obavestenja where student_id = ?", String.class, s.getId());
        assertEquals("NEPOZNAT", JSON.readTree(podaci).get("uredjaj").asString());
    }

    @Test
    void pomocValidacija() throws Exception {
        for (String telo : List.of("{}", "{\"indeks\":\"\"}", "{\"indeks\":\"   \"}",
                "{\"indeks\":\"" + "1".repeat(31) + "\"}", "nije json")) {
            mvc.perform(javniPost("/public/nalog/pomoc", telo)).andExpect(status().isBadRequest());
        }
        assertEquals(0, brojObavestenja());
    }

    // ---------------------------------------------------------------- isti odgovor bez obzira na uređaj, bravu i limit

    /** Status, telo i sva zaglavlja (sa Set-Cookie), da se odgovori porede bajt po bajt. */
    record Odgovor(int status, String telo, java.util.Map<String, List<String>> zaglavlja) {}

    static Odgovor odgovor(MvcResult r) throws Exception {
        MockHttpServletResponse res = r.getResponse();
        java.util.Map<String, List<String>> z = new java.util.TreeMap<>();
        for (String ime : res.getHeaderNames()) {
            z.put(ime, res.getHeaders(ime));
        }
        // greške nose vreme nastanka ("time"), jedino polje koje se sme razlikovati
        String telo = res.getContentAsString().replaceAll("\"time\":\"[^\"]*\"", "\"time\":\"*\"");
        return new Odgovor(res.getStatus(), telo, z);
    }

    @Test
    void pomocJeIstaSaPoznatimUredjajemIBezNjega() throws Exception {
        Student a = studenti.napravi("ana@example.test");
        Student b = studenti.napravi("ana@example.test");
        String tokenB = prijaviSe(b);
        // postojeći student: bez kolačića (NEPOZNAT) naspram sopstvenog uređaja (ZAPAMCEN)
        Odgovor nepoznat = odgovor(pomoc(a.getIndeks()));
        Odgovor zapamcen = odgovor(pomoc(b.getIndeks(), tokenB));
        assertEquals(200, nepoznat.status());
        assertEquals(nepoznat, zapamcen);
        assertFalse(nepoznat.zaglavlja().containsKey(HttpHeaders.SET_COOKIE));
        // nepostojeći indeks: bez kolačića naspram kolačića nekog pravog uređaja
        Odgovor nepostojeci1 = odgovor(pomoc(TestStudenti.noviIndeks()));
        Odgovor nepostojeci2 = odgovor(pomoc(TestStudenti.noviIndeks(), tokenB));
        assertEquals(nepoznat, nepostojeci1);
        assertEquals(nepoznat, nepostojeci2);
        // i da se uređaj stvarno razlikovao u obaveštenjima
        assertEquals("NEPOZNAT", JSON.readTree(jdbc.queryForObject(
                "select podaci from obavestenja where student_id = ?", String.class, a.getId())).get("uredjaj").asString());
        assertEquals("ZAPAMCEN", JSON.readTree(jdbc.queryForObject(
                "select podaci from obavestenja where student_id = ?", String.class, b.getId())).get("uredjaj").asString());
    }

    @Test
    void kodJeIstiSaPoznatimUredjajemIBezNjega() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String token = prijaviSe(s);
        // isti student i isti nepostojeći indeks dva puta (maska i lažna maska zavise samo od indeksa)
        Odgovor bez = odgovor(kod(s.getIndeks()));
        Odgovor sa = odgovor(mvc.perform(javniPost("/public/nalog/kod", "{\"indeks\":\"" + s.getIndeks() + "\"}")
                .cookie(student(token))).andReturn());
        assertEquals(200, bez.status());
        assertEquals(bez, sa);
        assertFalse(bez.zaglavlja().containsKey(HttpHeaders.SET_COOKIE));

        String nepostojeci = TestStudenti.noviIndeks();
        Odgovor nBez = odgovor(kod(nepostojeci));
        Odgovor nSa = odgovor(mvc.perform(javniPost("/public/nalog/kod", "{\"indeks\":\"" + nepostojeci + "\"}")
                .cookie(student(token))).andReturn());
        assertEquals(200, nBez.status());
        assertEquals(nBez, nSa);
        assertFalse(nBez.zaglavlja().containsKey(HttpHeaders.SET_COOKIE));
    }

    /** Brava indeksa zauzeta duže od čekanja (10 s): "Ne stiže mi kod" ćutke preskače, odgovor je isti kao uvek. */
    @Test
    void pomocJeIstaKadBravaIndeksaIstekne() throws Exception {
        Student normalan = studenti.napravi("ana@example.test");
        Student zakljucan = studenti.napravi("ana@example.test");
        Odgovor uobicajen = odgovor(pomoc(normalan.getIndeks()));
        assertEquals(200, uobicajen.status());

        String brava = PristupBravama.bravaPomoci(kodServis, zakljucan.getIndeks());
        Odgovor istekla;
        try (java.sql.Connection veza = dataSource.getConnection()) {
            try (var ps = veza.prepareStatement("SELECT GET_LOCK(?, 1)")) {
                ps.setString(1, brava);
                var rs = ps.executeQuery();
                assertTrue(rs.next() && rs.getInt(1) == 1);
            }
            istekla = odgovor(pomoc(zakljucan.getIndeks()));
            try (var ps = veza.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                ps.setString(1, brava);
                ps.executeQuery();
            }
        }
        assertEquals(uobicajen, istekla);
        // preskočeno: obaveštenje samo za studenta čija brava nije bila zauzeta
        assertEquals(1, brojObavestenja());
        assertEquals(normalan.getId(), jdbc.queryForObject("select student_id from obavestenja", Long.class));
    }

    /** 429 po indeksu je isti za postojećeg i nepostojećeg, iako se obaveštenje za postojećeg upisuje u pozadini. */
    @Test
    void odgovor429JeIstZaPostojecegINepostojecegIndeksa() throws Exception {
        jdbc.update("delete from obavestenja");
        Student s = studenti.napravi("ana@example.test");
        String nepostojeci = TestStudenti.noviIndeks();
        Odgovor poznat = null;
        Odgovor nepoznat = null;
        for (int i = 0; i < 4; i++) {
            ip = noviIp();
            poznat = odgovor(kod(s.getIndeks()));
            nepoznat = odgovor(kod(nepostojeci));
            if (i < 3) {
                assertEquals(200, poznat.status());
            }
        }
        assertEquals(429, poznat.status());
        assertEquals(poznat, nepoznat);
        assertFalse(poznat.zaglavlja().containsKey(HttpHeaders.SET_COOKIE));
        // obaveštenje stiže, ali posle odgovora
        long kraj = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (brojObavestenja() < 1 && System.nanoTime() < kraj) {
            Thread.sleep(20);
        }
        assertEquals(1, brojObavestenja());
    }
}
