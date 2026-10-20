package tri.novica.gfssystem.rest.lti;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;
import tri.novica.gfssystem.dto.lti.LtiRegistracijaCmd;
import tri.novica.gfssystem.dto.lti.LtiRegistracijaInfo;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.lti.LtiRegistracijaServis;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.prijava.TestStudenti;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Moodle LTI 1.3 (spec 8.3) kroz ceo bezbednosni lanac nad pravom MySQL bazom: login initiation, launch sa JWT-om koji
 * potpisuje test ključ "platforme", lokalni JWKS ({@link HttpServer} na slučajnom portu loopback-a, zato
 * {@code gfs.lti.dozvoli-http=true}), povezivanje naloga posle prijave kodom i sve greške. Svaki test ima svoju
 * registraciju (izmišljen Moodle na {@code example.test}) i svoju JWKS putanju (keš ključeva je po URL-u).
 */
@SpringBootTest(properties = "gfs.lti.dozvoli-http=true")
@AutoConfigureMockMvc
@Import(LazniMejlServis.class)
@ExtendWith(OutputCaptureExtension.class)
class LtiTokIT {

    static final String LEARNER = "http://purl.imsglobal.org/vocab/lis/v2/membership#Learner";
    static final String INSTRUCTOR = "http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor";
    static final String LTI = "https://purl.imsglobal.org/spec/lti/claim/";
    static final String PREFIKS = "https://lti-tok-it-";
    static final String VEC_POVEZAN = "Ovaj Moodle nalog je već povezan sa drugim studentom. Javi se nastavniku.";
    static final String ISTEKLO = "Povezivanje je isteklo. Otvori GFS ponovo iz Moodle-a.";
    static final Pattern KOD = Pattern.compile("\\b(\\d{6})\\b");

    static HttpServer jwksServer;
    /** JWKS po putanji ({@code /certs-<id>}); test može da ga zameni (rotacija ključa platforme). */
    static final Map<String, String> JWKS = new ConcurrentHashMap<>();
    static RSAKey kljucPlatforme;
    static RSAKey drugiKljuc;

    @Autowired MockMvc mvc;
    @Autowired LtiRegistracijaServis registracije;
    @Autowired LazniMejlServis mejl;
    @Autowired StudentRepository studentRepository;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;
    LtiRegistracijaInfo reg;
    String moodle;
    String jwksPutanja;
    String ip;
    final List<String> ipovi = new ArrayList<>();

    @BeforeAll
    static void platforma() throws Exception {
        kljucPlatforme = new RSAKeyGenerator(2048).keyID("moodle-kljuc-1").generate();
        // isti kid, drugi ključ: potpis mora pasti
        drugiKljuc = new RSAKeyGenerator(2048).keyID("moodle-kljuc-1").generate();
        jwksServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        jwksServer.createContext("/", x -> {
            String telo = JWKS.get(x.getRequestURI().getPath());
            byte[] b = (telo == null ? "{}" : telo).getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().add("Content-Type", "application/json");
            x.sendResponseHeaders(telo == null ? 404 : 200, b.length);
            try (OutputStream o = x.getResponseBody()) {
                o.write(b);
            }
        });
        jwksServer.start();
    }

    @AfterAll
    static void ugasi() {
        jwksServer.stop(0);
    }

    @BeforeEach
    void setUp() {
        mejl.ocisti();
        studenti = new TestStudenti(studentRepository, jdbc);
        String id = UUID.randomUUID().toString().substring(0, 8);
        moodle = PREFIKS + id + ".example.test";
        jwksPutanja = "/certs-" + id;
        JWKS.put(jwksPutanja, new JWKSet(kljucPlatforme.toPublicJWK()).toString());
        reg = registracije.napravi(new LtiRegistracijaCmd("Predmet LTI", moodle, "klijent-" + id, "5", "",
                "http://127.0.0.1:" + jwksServer.getAddress().getPort() + jwksPutanja, true));
        ip = noviIp();
    }

    @AfterEach
    void ocisti() {
        mejl.ocisti();
        studenti.obrisi();
        jdbc.update("delete from lti_registracije where izdavac like ?", PREFIKS + "%");
        jdbc.update("delete from spoljni_identiteti where izdavac like ?", PREFIKS + "%");
        jdbc.update("delete from obavestenja");
        for (String i : ipovi) {
            jdbc.update("delete from pokusaji where kljuc = ?", i);
        }
        JWKS.remove(jwksPutanja);
    }

    String noviIp() {
        String i = "2001:db8::1:" + UUID.randomUUID().toString().substring(0, 4);
        ipovi.add(i);
        return i;
    }

    RequestPostProcessor saIp() {
        return req -> {
            req.setRemoteAddr(ip);
            return req;
        };
    }

    // ---------------------------------------------------------------- pomoćnici

    /** Rezultat login initiation-a: parametri preusmerenja na Moodle i kolačić {@code gfs_lti}. */
    record Pocetak(String state, String nonce, String kolacic, Map<String, String> parametri, String location,
                   MockHttpServletResponse odgovor) {}

    MockHttpServletRequestBuilder prijavaZahtev() {
        return get("/public/lti/prijava").with(saIp())
                .param("iss", moodle)
                .param("login_hint", "42")
                .param("target_link_uri", "http://localhost:4200/api/public/lti/start")
                .param("lti_message_hint", "{\"cmid\":7,\"launchid\":\"a+b c&d=e\"}")
                .param("client_id", reg.clientId())
                .param("lti_deployment_id", reg.deploymentId());
    }

    Pocetak prijava() throws Exception {
        return pocetak(mvc.perform(prijavaZahtev()).andReturn().getResponse());
    }

    static Pocetak pocetak(MockHttpServletResponse r) {
        assertEquals(302, r.getStatus(), "prijava mora preusmeriti na Moodle");
        String location = r.getHeader(HttpHeaders.LOCATION);
        Map<String, String> p = new LinkedHashMap<>();
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().forEach((k, v) ->
                p.put(k, URLDecoder.decode(v.getFirst(), StandardCharsets.UTF_8)));
        return new Pocetak(p.get("state"), p.get("nonce"), vrednost(postavljen(r, Kolacici.LTI)), p, location, r);
    }

    JWTClaimsSet.Builder claims(Pocetak p, String sub, String... uloge) {
        Instant sada = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(moodle)
                .audience(reg.clientId())
                .subject(sub)
                .issueTime(Date.from(sada))
                .expirationTime(Date.from(sada.plusSeconds(60)))
                .claim("nonce", p.nonce())
                .claim("name", "Ana Anić")
                .claim("given_name", "Ana")
                .claim("family_name", "Anić")
                .claim(LTI + "deployment_id", reg.deploymentId())
                .claim(LTI + "message_type", "LtiResourceLinkRequest")
                .claim(LTI + "version", "1.3.0")
                .claim(LTI + "roles", List.of(uloge.length == 0 ? new String[]{LEARNER} : uloge));
    }

    static String potpisi(JWTClaimsSet c, RSAKey kljuc) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kljuc.getKeyID())
                .type(JOSEObjectType.JWT).build(), c);
        jwt.sign(new RSASSASigner(kljuc));
        return jwt.serialize();
    }

    String token(Pocetak p, String sub, Consumer<JWTClaimsSet.Builder> izmena) throws Exception {
        JWTClaimsSet.Builder b = claims(p, sub);
        izmena.accept(b);
        return potpisi(b.build(), kljucPlatforme);
    }

    /** Launch kakav šalje pregledač sa Moodle-a: cross-site {@code form_post} sa tuđim {@code Origin}. */
    MockHttpServletResponse start(String state, String idToken, Cookie... kolacici) throws Exception {
        MockHttpServletRequestBuilder b = post("/public/lti/start").with(saIp())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.ORIGIN, moodle)
                .header("Sec-Fetch-Site", "cross-site");
        if (state != null) b.param("state", state);
        if (idToken != null) b.param("id_token", idToken);
        if (kolacici.length > 0) b.cookie(kolacici);
        return mvc.perform(b).andReturn().getResponse();
    }

    MockHttpServletResponse start(Pocetak p, String idToken) throws Exception {
        return start(p.state(), idToken, new Cookie(Kolacici.LTI, p.kolacic()));
    }

    static void preusmerava(MockHttpServletResponse r, String location) {
        assertEquals(303, r.getStatus(), "launch odgovara sa 303");
        assertEquals(location, r.getHeader(HttpHeaders.LOCATION));
    }

    static String postavljen(MockHttpServletResponse r, String ime) {
        List<String> h = r.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith(ime + "=") && !c.contains("Max-Age=0")).toList();
        assertEquals(1, h.size(), "Set-Cookie: " + r.getHeaders(HttpHeaders.SET_COOKIE));
        return h.getFirst();
    }

    static boolean postavlja(MockHttpServletResponse r, String ime) {
        return r.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(c -> c.startsWith(ime + "=") && !c.contains("Max-Age=0"));
    }

    static boolean brise(MockHttpServletResponse r, String ime) {
        return r.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(c -> c.startsWith(ime + "=;") && c.contains("Max-Age=0"));
    }

    static String vrednost(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    /** Pun tok studenta bez veze: login, launch, kolačić {@code gfs_lti_par}. */
    String parZa(String sub) throws Exception {
        Pocetak p = prijava();
        MockHttpServletResponse r = start(p, token(p, sub, b -> {}));
        preusmerava(r, "/nalog/moodle");
        return vrednost(postavljen(r, Kolacici.LTI_PAR));
    }

    /** Prijava kodom sa mejla (isti tok kao {@code /nalog}); vraća vrednost kolačića {@code gfs_student}. */
    String prijaviKodom(Student s) throws Exception {
        int pre = mejl.poslate().size();
        mvc.perform(post("/public/nalog/kod").with(saIp()).header(HttpHeaders.ORIGIN, PrijavaTestUtil.ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"indeks\":\"" + s.getIndeks() + "\"}"))
                .andExpect(status().isOk());
        Matcher m = KOD.matcher(mejl.cekaj(pre + 1).get(pre).telo());
        assertTrue(m.find());
        MockHttpServletResponse r = mvc.perform(post("/public/nalog/prijava").with(saIp())
                        .header(HttpHeaders.ORIGIN, PrijavaTestUtil.ORIGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"indeks\":\"" + s.getIndeks() + "\",\"kod\":\"" + m.group(1) + "\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        return vrednost(postavljen(r, Kolacici.STUDENT));
    }

    MockHttpServletResponse povezi(String student, String par) throws Exception {
        MockHttpServletRequestBuilder b = post("/public/lti/povezi").with(saIp())
                .header(HttpHeaders.ORIGIN, PrijavaTestUtil.ORIGIN);
        List<Cookie> k = new ArrayList<>();
        if (student != null) k.add(new Cookie(Kolacici.STUDENT, student));
        if (par != null) k.add(new Cookie(Kolacici.LTI_PAR, par));
        if (!k.isEmpty()) b.cookie(k.toArray(Cookie[]::new));
        return mvc.perform(b).andReturn().getResponse();
    }

    MockHttpServletRequestBuilder povezivanje(String par) {
        MockHttpServletRequestBuilder b = get("/public/lti/povezivanje").with(saIp());
        return par == null ? b : b.cookie(new Cookie(Kolacici.LTI_PAR, par));
    }

    void vezi(Student s, String sub) {
        jdbc.update("""
                insert into spoljni_identiteti (student_id, vrsta, izdavac, subjekat, registracija_id, kreirano)
                values (?, 'LTI', ?, ?, ?, ?)""", s.getId(), moodle, sub, reg.id(), LocalDateTime.now());
    }

    int brojVeza(String sub) {
        return jdbc.queryForObject("select count(*) from spoljni_identiteti where izdavac = ? and subjekat = ?",
                Integer.class, moodle, sub);
    }

    // ---------------------------------------------------------------- login initiation

    @Test
    void prijavaPreusmeravaNaMoodleSaSvimParametrimaIKolacicem() throws Exception {
        Pocetak p = prijava();
        assertTrue(p.location().startsWith(moodle + "/mod/lti/auth.php?"), p.location());
        assertEquals("openid", p.parametri().get("scope"));
        assertEquals("id_token", p.parametri().get("response_type"));
        assertEquals("form_post", p.parametri().get("response_mode"));
        assertEquals("none", p.parametri().get("prompt"));
        assertEquals(reg.clientId(), p.parametri().get("client_id"));
        assertEquals("http://localhost:4200/api/public/lti/start", p.parametri().get("redirect_uri"));
        assertEquals("42", p.parametri().get("login_hint"));
        assertEquals("{\"cmid\":7,\"launchid\":\"a+b c&d=e\"}", p.parametri().get("lti_message_hint"));
        assertTrue(TokenGenerator.FORMAT.matcher(p.state()).matches(), "state");
        assertTrue(TokenGenerator.FORMAT.matcher(p.nonce()).matches(), "nonce");
        assertNotEquals(p.state(), p.nonce());

        String setCookie = postavljen(p.odgovor(), Kolacici.LTI);
        assertTrue(setCookie.contains("SameSite=None"), setCookie);
        assertTrue(setCookie.contains("Secure"), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);
        assertTrue(setCookie.contains("Max-Age=600"), setCookie);
        assertTrue(TokenGenerator.FORMAT.matcher(p.kolacic()).matches());
        assertEquals("no-store", p.odgovor().getHeader(HttpHeaders.CACHE_CONTROL));

        Map<String, Object> red = jdbc.queryForMap("select * from lti_stanja where stanje_hes = ?",
                TokenHash.od(p.state()));
        assertEquals(TokenHash.od(p.kolacic()), red.get("kolacic_hes"));
        assertEquals(p.nonce(), red.get("nonce"));
        assertEquals(reg.id(), ((Number) red.get("registracija_id")).longValue());
        assertNull(red.get("iskorisceno"));
        LocalDateTime istice = (LocalDateTime) red.get("istice");
        LocalDateTime kreirano = (LocalDateTime) red.get("kreirano");
        assertEquals(600, java.time.Duration.between(kreirano, istice).toSeconds());
        // ni state ni kolačić nisu u bazi kao takvi
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_stanja where stanje_hes = ? or kolacic_hes = ?",
                Integer.class, p.state(), p.kolacic()));
    }

    @Test
    void prijavaPostomRadiIsto() throws Exception {
        MockHttpServletResponse r = mvc.perform(post("/public/lti/prijava").with(saIp())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.ORIGIN, moodle).header("Sec-Fetch-Site", "cross-site")
                .param("iss", moodle).param("login_hint", "42")
                .param("target_link_uri", "http://localhost:4200/api/public/lti/start")
                .param("client_id", reg.clientId()).param("lti_deployment_id", reg.deploymentId()))
                .andReturn().getResponse();
        Pocetak p = pocetak(r);
        assertFalse(p.parametri().containsKey("lti_message_hint"), "bez hint-a se ne šalje prazan");
        assertNull(r.getHeader("Access-Control-Allow-Origin"), "LTI putanje nemaju CORS zaglavlja");
    }

    @Test
    void postojeciKolacicSeKoristiPonovo() throws Exception {
        // dva ulaska iz istog pregledača (dve kartice) vezana su za isti kolačić, pa oba launch-a prolaze
        Pocetak prvi = prijava();
        MockHttpServletResponse r = mvc.perform(prijavaZahtev().cookie(new Cookie(Kolacici.LTI, prvi.kolacic())))
                .andReturn().getResponse();
        Pocetak drugi = pocetak(r);
        assertEquals(prvi.kolacic(), drugi.kolacic());
        assertNotEquals(prvi.state(), drugi.state());
        preusmerava(start(prvi, token(prvi, "u-" + UUID.randomUUID(), b -> {})), "/nalog/moodle");
        preusmerava(start(drugi, token(drugi, "u-" + UUID.randomUUID(), b -> {})), "/nalog/moodle");
    }

    @Test
    void nepoznataIliIskljucenaRegistracija() throws Exception {
        String greska = "/nalog/moodle?greska=nepoznata-registracija";
        for (MockHttpServletRequestBuilder b : List.of(
                get("/public/lti/prijava").param("iss", "https://nepoznat.example.test").param("login_hint", "1")
                        .param("client_id", reg.clientId()).param("lti_deployment_id", reg.deploymentId()),
                get("/public/lti/prijava").param("iss", moodle).param("login_hint", "1")
                        .param("client_id", "nema").param("lti_deployment_id", "5"),
                get("/public/lti/prijava").param("iss", moodle).param("login_hint", "1")
                        .param("client_id", reg.clientId()).param("lti_deployment_id", "6"),
                get("/public/lti/prijava").param("login_hint", "1"),
                get("/public/lti/prijava"))) {
            MockHttpServletResponse r = mvc.perform(b.with(saIp())).andReturn().getResponse();
            assertEquals(302, r.getStatus());
            assertEquals(greska, r.getHeader(HttpHeaders.LOCATION));
            assertFalse(postavlja(r, Kolacici.LTI));
        }
        jdbc.update("update lti_registracije set aktivna = 0 where id = ?", reg.id());
        MockHttpServletResponse r = mvc.perform(prijavaZahtev()).andReturn().getResponse();
        assertEquals(greska, r.getHeader(HttpHeaders.LOCATION));
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_stanja where registracija_id = ?", Integer.class,
                reg.id()));
    }

    @Test
    void prijavaBezLoginHintaNijePodrzana() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/public/lti/prijava").with(saIp()).param("iss", moodle)
                .param("client_id", reg.clientId()).param("lti_deployment_id", reg.deploymentId()))
                .andReturn().getResponse();
        assertEquals(302, r.getStatus());
        assertEquals("/nalog/moodle?greska=nepodrzana-poruka", r.getHeader(HttpHeaders.LOCATION));
    }

    // ---------------------------------------------------------------- launch: ishodi

    @Test
    void learnerSaVezomDobijaStudentskiKolacic(CapturedOutput izlaz) throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String sub = "moodle-sub-" + UUID.randomUUID();
        vezi(s, sub);
        Pocetak p = prijava();
        MockHttpServletResponse r = start(p, token(p, sub, b -> {}));
        preusmerava(r, "/nalog");
        String setCookie = postavljen(r, Kolacici.STUDENT);
        assertTrue(setCookie.contains("SameSite=Lax") && setCookie.contains("Max-Age=31536000"), setCookie);
        assertFalse(postavlja(r, Kolacici.LTI_PAR));
        String token = vrednost(setCookie);
        assertEquals("LTI", jdbc.queryForObject("select nacin from studentski_uredjaji where token_hes = ?",
                String.class, TokenHash.od(token)));
        assertNotNull(jdbc.queryForObject("select poslednji_ulaz from spoljni_identiteti where subjekat = ?",
                LocalDateTime.class, sub));
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indeks").value(s.getIndeks()))
                .andExpect(jsonPath("$.moodlePovezan").value(true))
                .andExpect(jsonPath("$.uredjaji[0].nacin").value("LTI"));
        assertEquals("no-store", r.getHeader(HttpHeaders.CACHE_CONTROL));
        // log: nikad claim vrednosti, state, nonce ni kolačić
        for (String tajno : List.of(sub, "Ana Anić", p.state(), p.nonce(), p.kolacic(), token)) {
            assertFalse(izlaz.getAll().contains(tajno), "log sadrži vrednost koja ne sme u log");
        }
    }

    @Test
    void learnerBezVezePaKodPaPoveziPaDrugiUlazJeJedanKlik(CapturedOutput izlaz) throws Exception {
        Student s = studenti.napravi("pera@example.test");
        String sub = "moodle-sub-" + UUID.randomUUID();
        Pocetak p = prijava();
        MockHttpServletResponse r = start(p, token(p, sub, b -> {}));
        preusmerava(r, "/nalog/moodle");
        assertFalse(postavlja(r, Kolacici.STUDENT));
        String setCookie = postavljen(r, Kolacici.LTI_PAR);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);
        assertTrue(setCookie.contains("Max-Age=1800"), setCookie);
        String par = vrednost(setCookie);
        Map<String, Object> red = jdbc.queryForMap("select * from lti_povezivanja where token_hes = ?",
                TokenHash.od(par));
        assertEquals(sub, red.get("subjekat"));
        assertEquals("Ana Anić", red.get("ime"));
        assertEquals(reg.id(), ((Number) red.get("registracija_id")).longValue());

        mvc.perform(povezivanje(par)).andExpect(status().isOk()).andExpect(jsonPath("$.ime").value("Ana Anić"));

        String student = prijaviKodom(s);
        MockHttpServletResponse v = povezi(student, par);
        assertEquals(204, v.getStatus(), v.getContentAsString());
        assertTrue(brise(v, Kolacici.LTI_PAR));
        assertEquals(s.getId(), jdbc.queryForObject(
                "select student_id from spoljni_identiteti where vrsta = 'LTI' and izdavac = ? and subjekat = ?",
                Long.class, moodle, sub));
        assertEquals(reg.id(), jdbc.queryForObject(
                "select registracija_id from spoljni_identiteti where subjekat = ?", Long.class, sub));

        // povezivanje je jednokratno
        assertEquals(410, povezi(student, par).getStatus());
        mvc.perform(povezivanje(par)).andExpect(status().isGone());

        Pocetak drugi = prijava();
        MockHttpServletResponse d = start(drugi, token(drugi, sub, b -> {}));
        preusmerava(d, "/nalog");
        assertTrue(postavlja(d, Kolacici.STUDENT));
        assertFalse(izlaz.getAll().contains(sub), "sub ne sme u log");
        assertFalse(izlaz.getAll().contains("Ana Anić"), "ime ne sme u log");
    }

    @Test
    void instructorDobijaPorukuBezIkakveSesije() throws Exception {
        Pocetak p = prijava();
        String t = potpisi(claims(p, "nastavnik-sub", INSTRUCTOR, LEARNER).build(), kljucPlatforme);
        MockHttpServletResponse r = start(p, t);
        preusmerava(r, "/nalog/moodle?uloga=nastavnik");
        assertFalse(postavlja(r, Kolacici.STUDENT));
        assertFalse(postavlja(r, Kolacici.LTI_PAR));
        assertFalse(postavlja(r, Kolacici.NASTAVNIK));
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_povezivanja where subjekat = 'nastavnik-sub'",
                Integer.class));
    }

    @Test
    void administratorJeKaoNastavnik() throws Exception {
        Pocetak p = prijava();
        String t = potpisi(claims(p, "admin-sub",
                "http://purl.imsglobal.org/vocab/lis/v2/institution/person#Administrator").build(), kljucPlatforme);
        preusmerava(start(p, t), "/nalog/moodle?uloga=nastavnik");
    }

    @Test
    void ulogaKojuNeznamoNijePodrzana() throws Exception {
        Pocetak p = prijava();
        String t = potpisi(claims(p, "gost", "http://purl.imsglobal.org/vocab/lis/v2/membership#Mentor").build(),
                kljucPlatforme);
        preusmerava(start(p, t), "/nalog/moodle?greska=nepodrzana-poruka");
    }

    @Test
    void startProlaziProveruPoreklaBezOriginaIBezSecFetchSite() throws Exception {
        Pocetak p = prijava();
        MockHttpServletResponse r = mvc.perform(post("/public/lti/start").with(saIp())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("state", p.state()).param("id_token", token(p, "u-" + UUID.randomUUID(), b -> {}))
                        .cookie(new Cookie(Kolacici.LTI, p.kolacic())))
                .andReturn().getResponse();
        preusmerava(r, "/nalog/moodle");
    }

    @Test
    void startSaTudjimOriginomNemaCorsZaglavljaINijeOdbijen() throws Exception {
        Pocetak p = prijava();
        MockHttpServletResponse r = start(p, token(p, "u-" + UUID.randomUUID(), b -> {}));
        preusmerava(r, "/nalog/moodle");
        assertNull(r.getHeader("Access-Control-Allow-Origin"));
        assertNull(r.getHeader("Access-Control-Allow-Credentials"));
    }

    // ---------------------------------------------------------------- launch: greške

    void greska(MockHttpServletResponse r, String kod) {
        preusmerava(r, "/nalog/moodle?greska=" + kod);
        assertFalse(postavlja(r, Kolacici.STUDENT), "greška ne prijavljuje");
        assertFalse(postavlja(r, Kolacici.LTI_PAR), "greška ne započinje povezivanje");
    }

    /** Deo loga posle {@code od} mora imati {@code razlog=<reč>} (test dokazuje zašto je token odbijen, ne samo da jeste). */
    static void razlog(CapturedOutput izlaz, int od, String rec) {
        String novo = izlaz.getAll().substring(od);
        Matcher m = Pattern.compile("razlog=([^,\\s]+(?:,[^,\\s]+)*)").matcher(novo);
        assertTrue(m.find(), "nema razloga u logu: " + novo);
        assertTrue(List.of(m.group(1).split(",")).contains(rec), "očekivan razlog " + rec + ", log: " + novo);
    }

    @Test
    void pogresanAud(CapturedOutput izlaz) throws Exception {
        Pocetak p = prijava();
        int od = izlaz.getAll().length();
        greska(start(p, token(p, "u1", b -> b.audience("drugi-klijent"))), "neispravan-token");
        razlog(izlaz, od, "aud");
        assertFalse(izlaz.getAll().contains("drugi-klijent"), "vrednost claim-a ne sme u log");
    }

    @Test
    void audNizTraziAzpJednakClientId(CapturedOutput izlaz) throws Exception {
        Pocetak a = prijava();
        int od = izlaz.getAll().length();
        greska(start(a, token(a, "u1", b -> b.audience(List.of(reg.clientId(), "drugi")))), "neispravan-token");
        razlog(izlaz, od, "azp");
        Pocetak b2 = prijava();
        od = izlaz.getAll().length();
        greska(start(b2, token(b2, "u1", b -> b.audience(List.of(reg.clientId(), "drugi")).claim("azp", "drugi"))),
                "neispravan-token");
        razlog(izlaz, od, "azp");
        Pocetak c = prijava();
        preusmerava(start(c, token(c, "u-" + UUID.randomUUID(),
                b -> b.audience(List.of(reg.clientId(), "drugi")).claim("azp", reg.clientId()))), "/nalog/moodle");
        // azp i uz jedan aud mora biti Client ID
        Pocetak d = prijava();
        od = izlaz.getAll().length();
        greska(start(d, token(d, "u1", b -> b.claim("azp", "drugi"))), "neispravan-token");
        razlog(izlaz, od, "azp");
    }

    @Test
    void istiStateDrugiPut() throws Exception {
        Pocetak p = prijava();
        String t = token(p, "u-" + UUID.randomUUID(), b -> {});
        preusmerava(start(p, t), "/nalog/moodle");
        greska(start(p, t), "istekla-sesija");
    }

    @Test
    void istovremeniLaunchSaIstimStateomProlaziSamoJednom() throws Exception {
        Pocetak p = prijava();
        String t = token(p, "u-" + UUID.randomUUID(), b -> {});
        int n = 4;
        ExecutorService izvrsilac = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch kreni = new CountDownLatch(1);
            List<Future<String>> f = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                f.add(izvrsilac.submit(() -> {
                    kreni.await();
                    return start(p, t).getHeader(HttpHeaders.LOCATION);
                }));
            }
            kreni.countDown();
            int uspesnih = 0;
            for (Future<String> x : f) {
                if ("/nalog/moodle".equals(x.get(120, TimeUnit.SECONDS))) uspesnih++;
            }
            assertEquals(1, uspesnih);
        } finally {
            izvrsilac.shutdownNow();
        }
    }

    @Test
    void nonceNeOdgovara(CapturedOutput izlaz) throws Exception {
        Pocetak p = prijava();
        int od = izlaz.getAll().length();
        greska(start(p, token(p, "u1", b -> b.claim("nonce", "drugi-nonce"))), "neispravan-token");
        razlog(izlaz, od, "nonce");
        Pocetak q = prijava();
        od = izlaz.getAll().length();
        greska(start(q, token(q, "u1", b -> b.claim("nonce", null))), "neispravan-token");
        razlog(izlaz, od, "nonce");
        assertFalse(izlaz.getAll().contains("drugi-nonce"), "vrednost claim-a ne sme u log");
    }

    @Test
    void nonceIzDrugogStanjaNeProlazi(CapturedOutput izlaz) throws Exception {
        Pocetak a = prijava();
        Pocetak b = prijava();
        int od = izlaz.getAll().length();
        // token napravljen za stanje a, poslat uz stanje b
        greska(start(b, token(a, "u1", x -> {})), "neispravan-token");
        razlog(izlaz, od, "nonce");
    }

    @Test
    void istekaoTokenIVremeUBuducnosti(CapturedOutput izlaz) throws Exception {
        Instant sada = Instant.now();
        Pocetak a = prijava();
        int od = izlaz.getAll().length();
        greska(start(a, token(a, "u1", b -> b.expirationTime(Date.from(sada.minusSeconds(120)))
                .issueTime(Date.from(sada.minusSeconds(300))))), "neispravan-token");
        razlog(izlaz, od, "exp");
        Pocetak b = prijava();
        od = izlaz.getAll().length();
        greska(start(b, token(b, "u1", x -> x.expirationTime(null))), "neispravan-token");
        razlog(izlaz, od, "exp");
        Pocetak c = prijava();
        od = izlaz.getAll().length();
        greska(start(c, token(c, "u1", x -> x.issueTime(Date.from(sada.plusSeconds(300)))
                .expirationTime(Date.from(sada.plusSeconds(600))))), "neispravan-token");
        razlog(izlaz, od, "iat");
        Pocetak d = prijava();
        od = izlaz.getAll().length();
        greska(start(d, token(d, "u1", x -> x.issueTime(null))), "neispravan-token");
        razlog(izlaz, od, "iat");
        // u okviru leeway-a od 60 s: prolazi
        Pocetak e = prijava();
        preusmerava(start(e, token(e, "u-" + UUID.randomUUID(), x -> x.expirationTime(Date.from(sada.minusSeconds(20)))
                .issueTime(Date.from(sada.minusSeconds(80))))), "/nalog/moodle");
    }

    @Test
    void pogresanDeploymentIIzdavac(CapturedOutput izlaz) throws Exception {
        Pocetak a = prijava();
        int od = izlaz.getAll().length();
        greska(start(a, token(a, "u1", b -> b.claim(LTI + "deployment_id", "6"))), "neispravan-token");
        razlog(izlaz, od, "deployment_id");
        Pocetak b = prijava();
        od = izlaz.getAll().length();
        greska(start(b, token(b, "u1", x -> x.claim(LTI + "deployment_id", null))), "neispravan-token");
        razlog(izlaz, od, "deployment_id");
        Pocetak c = prijava();
        od = izlaz.getAll().length();
        greska(start(c, token(c, "u1", x -> x.issuer("https://drugi-moodle.example.test"))), "neispravan-token");
        razlog(izlaz, od, "iss");
        assertFalse(izlaz.getAll().contains("drugi-moodle"), "vrednost claim-a ne sme u log");
    }

    @Test
    void nepoznatKidIPotpisDrugimKljucem(CapturedOutput izlaz) throws Exception {
        Pocetak a = prijava();
        RSAKey nepoznat = new RSAKeyGenerator(2048).keyID("nepoznat-kid").generate();
        int od = izlaz.getAll().length();
        greska(start(a, potpisi(claims(a, "u1").build(), nepoznat)), "neispravan-token");
        razlog(izlaz, od, "potpis");
        Pocetak b = prijava();
        od = izlaz.getAll().length();
        greska(start(b, potpisi(claims(b, "u1").build(), drugiKljuc)), "neispravan-token");
        razlog(izlaz, od, "potpis");
    }

    @Test
    void nepotpisanIHmacTokenSeOdbijaju() throws Exception {
        Pocetak a = prijava();
        greska(start(a, new PlainJWT(claims(a, "u1").build()).serialize()), "neispravan-token");
        Pocetak b = prijava();
        SignedJWT hs = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(b, "u1").build());
        hs.sign(new MACSigner("x".repeat(32).getBytes(StandardCharsets.UTF_8)));
        greska(start(b, hs.serialize()), "neispravan-token");
        Pocetak c = prijava();
        greska(start(c, "nije.jwt.uopste"), "neispravan-token");
        Pocetak d = prijava();
        greska(start(d.state(), null, new Cookie(Kolacici.LTI, d.kolacic())), "neispravan-token");
    }

    @Test
    void rotacijaKljucaPlatformeSeHvataNaNepoznatKid() throws Exception {
        Pocetak a = prijava();
        preusmerava(start(a, token(a, "u-" + UUID.randomUUID(), b -> {})), "/nalog/moodle");
        RSAKey novi = new RSAKeyGenerator(2048).keyID("moodle-kljuc-2").generate();
        JWKS.put(jwksPutanja, new JWKSet(List.of(kljucPlatforme.toPublicJWK(), novi.toPublicJWK())).toString());
        Pocetak b = prijava();
        preusmerava(start(b, potpisi(claims(b, "u-" + UUID.randomUUID()).build(), novi)), "/nalog/moodle");
    }

    @Test
    void nepodrzanaVrstaPorukeIVerzija() throws Exception {
        Pocetak a = prijava();
        greska(start(a, token(a, "u1", b -> b.claim(LTI + "message_type", "LtiDeepLinkingRequest"))),
                "nepodrzana-poruka");
        Pocetak b = prijava();
        greska(start(b, token(b, "u1", x -> x.claim(LTI + "version", "1.1"))), "nepodrzana-poruka");
    }

    @Test
    void bezSubJeNeispravanToken(CapturedOutput izlaz) throws Exception {
        Pocetak a = prijava();
        int od = izlaz.getAll().length();
        greska(start(a, token(a, null, b -> {})), "neispravan-token");
        razlog(izlaz, od, "sub");
    }

    @Test
    void subKaoBrojNijeTekstPaJeNeispravanToken(CapturedOutput izlaz) throws Exception {
        // biblioteke tiho pretvore broj u tekst ("123"): sub mora biti JSON string u izvornom tokenu
        Pocetak a = prijava();
        int od = izlaz.getAll().length();
        greska(start(a, token(a, "u1", b -> b.claim("sub", 123))), "neispravan-token");
        razlog(izlaz, od, "sub");
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_povezivanja where registracija_id = ?",
                Integer.class, reg.id()));
    }

    @Test
    void nbfUBuducnostiJeNeispravanToken(CapturedOutput izlaz) throws Exception {
        Pocetak a = prijava();
        int od = izlaz.getAll().length();
        greska(start(a, token(a, "u1", b -> b.notBeforeTime(Date.from(Instant.now().plusSeconds(300))))),
                "neispravan-token");
        razlog(izlaz, od, "nbf");
    }

    @Test
    void launchBezKolacicaGfsLti() throws Exception {
        Pocetak p = prijava();
        MockHttpServletResponse r = start(p.state(), token(p, "u1", b -> {}));
        greska(r, "bez-kolacica");
        // provera se ne slabi: ni kasnije isti state ne prolazi
        greska(start(p, token(p, "u1", b -> {})), "istekla-sesija");
    }

    @Test
    void tudjKolacicGfsLti() throws Exception {
        Pocetak p = prijava();
        Pocetak drugi = prijava();
        greska(start(p.state(), token(p, "u1", b -> {}), new Cookie(Kolacici.LTI, "x" + drugi.kolacic().substring(1))),
                "istekla-sesija");
    }

    @Test
    void podmetnutKolacicPoredPravogNeSmeta() throws Exception {
        Pocetak p = prijava();
        String podmetnut = new TokenGenerator().novi();
        preusmerava(start(p.state(), token(p, "u-" + UUID.randomUUID(), b -> {}),
                new Cookie(Kolacici.LTI, podmetnut), new Cookie(Kolacici.LTI, p.kolacic())), "/nalog/moodle");
    }

    @Test
    void nepoznatIstekaoIliBezStatea() throws Exception {
        Pocetak p = prijava();
        String t = token(p, "u1", b -> {});
        greska(start(new TokenGenerator().novi(), t, new Cookie(Kolacici.LTI, p.kolacic())), "istekla-sesija");
        greska(start(null, t, new Cookie(Kolacici.LTI, p.kolacic())), "istekla-sesija");
        greska(start("kratak", t, new Cookie(Kolacici.LTI, p.kolacic())), "istekla-sesija");
        jdbc.update("update lti_stanja set istice = ? where stanje_hes = ?", LocalDateTime.now().minusSeconds(1),
                TokenHash.od(p.state()));
        greska(start(p, t), "istekla-sesija");
    }

    @Test
    void registracijaIskljucenaIzmedjuPrijaveILauncha() throws Exception {
        Pocetak p = prijava();
        jdbc.update("update lti_registracije set aktivna = 0 where id = ?", reg.id());
        greska(start(p, token(p, "u1", b -> {})), "nepoznata-registracija");
    }

    @Test
    void kolacicLtiImaPrefiksHostISamoTeAtribute() throws Exception {
        Pocetak p = prijava();
        assertEquals("__Host-gfs_lti", Kolacici.LTI);
        String setCookie = postavljen(p.odgovor(), "__Host-gfs_lti");
        assertTrue(setCookie.startsWith("__Host-gfs_lti=" + p.kolacic() + ";"), setCookie);
        // __Host- traži Secure, Path=/ i nijedan Domain; ovde su to tačno ovi atributi (Expires prati Max-Age)
        List<String> atributi = new ArrayList<>(List.of(setCookie.split("; ")));
        atributi.removeFirst();
        assertTrue(atributi.removeIf(a -> a.startsWith("Expires=")), setCookie);
        assertEquals(java.util.Set.of("Path=/", "Max-Age=600", "Secure", "HttpOnly", "SameSite=None"),
                new java.util.HashSet<>(atributi), setCookie);
        assertEquals(5, atributi.size(), setCookie);
        assertFalse(setCookie.toLowerCase().contains("domain"), setCookie);
        assertFalse(postavlja(p.odgovor(), "gfs_lti"), "staro ime se više ne postavlja");
    }

    @Test
    void launchCitaSamoKolacicSaPrefiksomHost() throws Exception {
        Pocetak p = prijava();
        // stari naziv ne vezuje launch za pregledač
        greska(start(p.state(), token(p, "u1", b -> {}), new Cookie("gfs_lti", p.kolacic())), "bez-kolacica");
        Pocetak q = prijava();
        preusmerava(start(q.state(), token(q, "u-" + UUID.randomUUID(), b -> {}),
                new Cookie("__Host-gfs_lti", q.kolacic())), "/nalog/moodle");
    }

    @Test
    void predugackSubJeNeispravanToken() throws Exception {
        // sub dug 256 znakova prelazi najveću dužinu i ne sme stići do kolone varchar(255)
        Pocetak a = prijava();
        greska(start(a, token(a, "s".repeat(256), b -> {})), "neispravan-token");
    }

    @Test
    void imeBezNevidljivihZnakovaISmeraPisanja() throws Exception {
        Pocetak a = prijava();
        String par = vrednost(postavljen(start(a, token(a, "u-" + UUID.randomUUID(),
                b -> b.claim("name", "Ana\u202E Anić "))), Kolacici.LTI_PAR));
        mvc.perform(povezivanje(par)).andExpect(jsonPath("$.ime").value("Ana Anić"));

        Pocetak b = prijava();
        String niz = vrednost(postavljen(start(b, token(b, "u-" + UUID.randomUUID(),
                x -> x.claim("name", "Ana\u200B\u2066\u2028  \t Anić\u00AD\uE000"))), Kolacici.LTI_PAR));
        mvc.perform(povezivanje(niz)).andExpect(jsonPath("$.ime").value("Ana Anić"));

        Pocetak c = prijava();
        String razmaci = vrednost(postavljen(start(c, token(c, "u-" + UUID.randomUUID(),
                x -> x.claim("name", "Ana    Anić"))), Kolacici.LTI_PAR));
        mvc.perform(povezivanje(razmaci)).andExpect(jsonPath("$.ime").value("Ana Anić"));

        // ime od samih nevidljivih znakova je kao da ga nema
        Pocetak d = prijava();
        String prazno = vrednost(postavljen(start(d, token(d, "u-" + UUID.randomUUID(),
                x -> x.claim("name", "\u202E\u200B").claim("given_name", null).claim("family_name", null))),
                Kolacici.LTI_PAR));
        mvc.perform(povezivanje(prazno)).andExpect(jsonPath("$.ime").isEmpty());
    }

    @Test
    void corsOstajeZaPoveziIPovezivanje() throws Exception {
        for (String[] t : new String[][]{{"/public/lti/povezi", "POST"}, {"/public/lti/povezivanje", "GET"}}) {
            MockHttpServletResponse r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .options(t[0]).with(saIp())
                            .header(HttpHeaders.ORIGIN, "https://napadac.example.com")
                            .header("Access-Control-Request-Method", t[1]))
                    .andReturn().getResponse();
            assertEquals(403, r.getStatus(), t[0]);
            assertNull(r.getHeader("Access-Control-Allow-Origin"), t[0]);
        }
    }

    @Test
    void startSaKosomCrtomNijeIzuzet() throws Exception {
        Pocetak p = prijava();
        MockHttpServletResponse r = mvc.perform(post("/public/lti/start/").with(saIp())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.ORIGIN, moodle).header("Sec-Fetch-Site", "cross-site")
                        .param("state", p.state()).param("id_token", token(p, "u-" + UUID.randomUUID(), b -> {}))
                        .cookie(new Cookie(Kolacici.LTI, p.kolacic())))
                .andReturn().getResponse();
        assertNotEquals(303, r.getStatus(), "putanja sa kosom crtom nije launch");
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_stanja where stanje_hes = ? and iskorisceno is not null",
                Integer.class, TokenHash.od(p.state())), "state nije potrošen");
    }

    // ---------------------------------------------------------------- povezivanje

    @Test
    void poveziSubKojiJeVecVezanZaDrugogJe409() throws Exception {
        Student prvi = studenti.napravi("prvi@example.test");
        Student drugi = studenti.napravi("drugi@example.test");
        String sub = "moodle-sub-" + UUID.randomUUID();
        String par = parZa(sub);
        vezi(prvi, sub);
        MockHttpServletResponse r = povezi(prijaviKodom(drugi), par);
        assertEquals(409, r.getStatus());
        assertTrue(r.getContentAsString().contains(VEC_POVEZAN), r.getContentAsString());
        assertEquals(prvi.getId(), jdbc.queryForObject("select student_id from spoljni_identiteti where subjekat = ?",
                Long.class, sub));
        assertEquals(1, brojVeza(sub));
    }

    @Test
    void poveziZaIstogStudentaJe204BezPromene() throws Exception {
        Student s = studenti.napravi("isti@example.test");
        String sub = "moodle-sub-" + UUID.randomUUID();
        String par = parZa(sub);
        vezi(s, sub);
        Long id = jdbc.queryForObject("select id from spoljni_identiteti where subjekat = ?", Long.class, sub);
        MockHttpServletResponse r = povezi(prijaviKodom(s), par);
        assertEquals(204, r.getStatus());
        assertEquals(1, brojVeza(sub));
        assertEquals(id, jdbc.queryForObject("select id from spoljni_identiteti where subjekat = ?", Long.class, sub));
    }

    @Test
    void poveziBezStudentaJe401BezParaJe410() throws Exception {
        Student s = studenti.napravi("x@example.test");
        String par = parZa("moodle-sub-" + UUID.randomUUID());
        MockHttpServletResponse bezStudenta = povezi(null, par);
        assertEquals(401, bezStudenta.getStatus());
        String student = prijaviKodom(s);
        MockHttpServletResponse bezPara = povezi(student, null);
        assertEquals(410, bezPara.getStatus());
        assertTrue(bezPara.getContentAsString().contains(ISTEKLO), bezPara.getContentAsString());
        assertEquals(410, povezi(student, new TokenGenerator().novi()).getStatus());
        assertEquals(410, povezi(student, "kratak").getStatus());
        jdbc.update("update lti_povezivanja set istice = ? where token_hes = ?", LocalDateTime.now().minusSeconds(1),
                TokenHash.od(par));
        assertEquals(410, povezi(student, par).getStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from spoljni_identiteti where student_id = ?",
                Integer.class, s.getId()));
    }

    @Test
    void poveziTraziProveruPorekla() throws Exception {
        Student s = studenti.napravi("y@example.test");
        String par = parZa("moodle-sub-" + UUID.randomUUID());
        String student = prijaviKodom(s);
        mvc.perform(post("/public/lti/povezi").with(saIp()).header(HttpHeaders.ORIGIN, moodle)
                        .cookie(new Cookie(Kolacici.STUDENT, student), new Cookie(Kolacici.LTI_PAR, par)))
                .andExpect(status().isForbidden());
    }

    @Test
    void povezivanjeVracaImeINeTrosi() throws Exception {
        Student s = studenti.napravi("z@example.test");
        String par = parZa("moodle-sub-" + UUID.randomUUID());
        for (int i = 0; i < 2; i++) {
            mvc.perform(povezivanje(par)).andExpect(status().isOk()).andExpect(jsonPath("$.ime").value("Ana Anić"));
        }
        assertEquals(204, povezi(prijaviKodom(s), par).getStatus());
        mvc.perform(povezivanje(par)).andExpect(status().isGone())
                .andExpect(jsonPath("$.reason").value(ISTEKLO));
    }

    @Test
    void povezivanjeBezImenaIzImenaIPrezimenaISkraceno() throws Exception {
        Pocetak a = prijava();
        MockHttpServletResponse r = start(a, token(a, "u-" + UUID.randomUUID(), b -> b.claim("name", null)
                .claim("given_name", null).claim("family_name", null)));
        String bez = vrednost(postavljen(r, Kolacici.LTI_PAR));
        mvc.perform(povezivanje(bez)).andExpect(status().isOk()).andExpect(jsonPath("$.ime").isEmpty());
        assertTrue(mvc.perform(povezivanje(bez)).andReturn().getResponse().getContentAsString().contains("\"ime\":null"));

        Pocetak b = prijava();
        String delovi = vrednost(postavljen(start(b, token(b, "u-" + UUID.randomUUID(), x -> x.claim("name", "  ")
                .claim("given_name", " Mika ").claim("family_name", "Mikić "))), Kolacici.LTI_PAR));
        mvc.perform(povezivanje(delovi)).andExpect(jsonPath("$.ime").value("Mika Mikić"));

        Pocetak c = prijava();
        String dugo = vrednost(postavljen(start(c, token(c, "u-" + UUID.randomUUID(),
                x -> x.claim("name", "  " + "Ž".repeat(150) + "  "))), Kolacici.LTI_PAR));
        mvc.perform(povezivanje(dugo)).andExpect(jsonPath("$.ime").value("Ž".repeat(100)));
    }

    @Test
    void povezivanjeBezKolacicaIstekloIliNepoznato() throws Exception {
        mvc.perform(povezivanje(null)).andExpect(status().isGone()).andExpect(jsonPath("$.reason").value(ISTEKLO));
        mvc.perform(povezivanje(new TokenGenerator().novi())).andExpect(status().isGone());
        String par = parZa("moodle-sub-" + UUID.randomUUID());
        jdbc.update("update lti_povezivanja set istice = ? where token_hes = ?", LocalDateTime.now().minusSeconds(1),
                TokenHash.od(par));
        mvc.perform(povezivanje(par)).andExpect(status().isGone());
    }

    // ---------------------------------------------------------------- sub se poredi binarno

    @Test
    void subOdVelikihIMalihSlovaSuRazliciteVeze() throws Exception {
        Student prvi = studenti.napravi("aa@example.test");
        Student drugi = studenti.napravi("bb@example.test");
        vezi(prvi, "aB");
        vezi(drugi, "Ab");          // ne sme biti duplikat prvog
        vezi(studenti.napravi("cc@example.test"), "a");
        vezi(studenti.napravi("dd@example.test"), "\u00E1");   // a sa oštrim akcentom
        assertEquals(1, brojVeza("aB"));
        assertEquals(1, brojVeza("Ab"));
        assertEquals(1, brojVeza("a"));
        assertEquals(1, brojVeza("\u00E1"));

        Pocetak p = prijava();
        MockHttpServletResponse r = start(p, token(p, "aB", b -> {}));
        preusmerava(r, "/nalog");
        String student = vrednost(postavljen(r, Kolacici.STUDENT));
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indeks").value(prvi.getIndeks()));

        Pocetak q = prijava();
        MockHttpServletResponse r2 = start(q, token(q, "Ab", b -> {}));
        String student2 = vrednost(postavljen(r2, Kolacici.STUDENT));
        mvc.perform(get("/public/nalog/ja").cookie(new Cookie(Kolacici.STUDENT, student2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indeks").value(drugi.getIndeks()));
    }

    @Test
    void poveziSubKojiSeRazlikujeSamoUVelicini() throws Exception {
        Student prvi = studenti.napravi("ee@example.test");
        Student drugi = studenti.napravi("ff@example.test");
        vezi(prvi, "xY");
        String par = parZa("Xy");
        assertEquals(204, povezi(prijaviKodom(drugi), par).getStatus());
        assertEquals(1, brojVeza("xY"));
        assertEquals(1, brojVeza("Xy"));
    }
}
