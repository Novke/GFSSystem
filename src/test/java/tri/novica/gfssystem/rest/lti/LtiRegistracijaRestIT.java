package tri.novica.gfssystem.rest.lti;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code /lti/registracije} i {@code /public/lti/jwks} kroz ceo bezbednosni lanac (spec 8.1, 8.2): nastavnik
 * upravlja registracijama, JWKS je javan i nosi samo javne delove ključa.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LtiRegistracijaRestIT {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PREFIKS = "https://lti-it-";

    @Autowired MockMvc mvc;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired JdbcTemplate jdbc;

    RequestPostProcessor nastavnik;
    String moodle;

    @BeforeEach
    void setUp() {
        nastavnik = PrijavaTestUtil.kaoNastavnik(PrijavaTestUtil.nastavnickiKolacic(nastavnici));
        moodle = PREFIKS + UUID.randomUUID().toString().substring(0, 8) + ".example.test";
    }

    @AfterEach
    void ocisti() {
        jdbc.update("delete from lti_registracije where izdavac like ?", PREFIKS + "%");
        jdbc.update("delete from nastavnicke_sesije where ip = ?", PrijavaTestUtil.IP);
    }

    String telo(String naziv, String izdavac, String clientId, String deploymentId, String authUrl, String jwksUrl,
                boolean aktivna) {
        return "{\"naziv\":\"" + naziv + "\",\"izdavac\":\"" + izdavac + "\",\"clientId\":\"" + clientId
                + "\",\"deploymentId\":\"" + deploymentId + "\",\"authUrl\":\"" + authUrl + "\",\"jwksUrl\":\""
                + jwksUrl + "\",\"aktivna\":" + aktivna + "}";
    }

    ResultActions upisi(String putanja, boolean put, String telo) throws Exception {
        var b = put ? put(putanja) : post(putanja);
        return mvc.perform(b.with(nastavnik).contentType(MediaType.APPLICATION_JSON).content(telo));
    }

    JsonNode napravi(String clientId, String deploymentId) throws Exception {
        MvcResult r = upisi("/lti/registracije", false, telo("Predmet A", moodle, clientId, deploymentId, "", "", true))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void bezSesijeJe401() throws Exception {
        mvc.perform(get("/lti/registracije")).andExpect(status().isUnauthorized());
        mvc.perform(get("/lti/registracije/vrednosti")).andExpect(status().isUnauthorized());
        mvc.perform(post("/lti/registracije").header(HttpHeaders.ORIGIN, PrijavaTestUtil.ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(telo("x", moodle, "c", "d", "", "", true)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void praznaAdresaSeIzvodiIzIzdavaca() throws Exception {
        JsonNode r = napravi("klijent-1", "1");
        assertTrue(r.get("id").asLong() > 0);
        assertEquals("Predmet A", r.get("naziv").asText());
        assertEquals(moodle, r.get("izdavac").asText());
        assertEquals("klijent-1", r.get("clientId").asText());
        assertEquals("1", r.get("deploymentId").asText());
        assertEquals(moodle + "/mod/lti/auth.php", r.get("authUrl").asText());
        assertEquals(moodle + "/mod/lti/certs.php", r.get("jwksUrl").asText());
        assertTrue(r.get("aktivna").asBoolean());
        assertFalse(r.get("kreirano").asText().isBlank());
    }

    @Test
    void izdavacSaZavrsnomKosomCrtomDajeIspravneAdrese() throws Exception {
        MvcResult m = upisi("/lti/registracije", false, telo("S", moodle + "/", "k", "1", "", "", true))
                .andExpect(status().isCreated()).andReturn();
        JsonNode r = JSON.readTree(m.getResponse().getContentAsString());
        assertEquals(moodle + "/mod/lti/auth.php", r.get("authUrl").asText());
        assertEquals(moodle + "/mod/lti/certs.php", r.get("jwksUrl").asText());
    }

    @Test
    void izricitaAdresaSeCuva() throws Exception {
        MvcResult m = upisi("/lti/registracije", false,
                telo("Drugi", moodle, "k", "1", moodle + "/moj/auth", moodle + "/moj/certs", true))
                .andExpect(status().isCreated()).andReturn();
        JsonNode r = JSON.readTree(m.getResponse().getContentAsString());
        assertEquals(moodle + "/moj/auth", r.get("authUrl").asText());
        assertEquals(moodle + "/moj/certs", r.get("jwksUrl").asText());
    }

    @Test
    void listaVracaNapravljenuRegistraciju() throws Exception {
        long id = napravi("klijent-2", "1").get("id").asLong();
        MvcResult r = mvc.perform(get("/lti/registracije").with(nastavnik)).andExpect(status().isOk()).andReturn();
        JsonNode lista = JSON.readTree(r.getResponse().getContentAsString());
        assertTrue(lista.isArray());
        boolean nadjena = false;
        for (JsonNode n : lista) {
            if (n.get("id").asLong() == id) nadjena = true;
        }
        assertTrue(nadjena);
    }

    @Test
    void izmenaMenjaPoljaINeDirneKreirano() throws Exception {
        JsonNode nova = napravi("klijent-3", "1");
        long id = nova.get("id").asLong();
        MvcResult m = upisi("/lti/registracije/" + id, true,
                telo("Preimenovano", moodle, "klijent-3", "2", "", "", false)).andExpect(status().isOk()).andReturn();
        JsonNode r = JSON.readTree(m.getResponse().getContentAsString());
        assertEquals(id, r.get("id").asLong());
        assertEquals("Preimenovano", r.get("naziv").asText());
        assertEquals("2", r.get("deploymentId").asText());
        assertFalse(r.get("aktivna").asBoolean());
        assertEquals(nova.get("kreirano").asText(), r.get("kreirano").asText());
    }

    @Test
    void izmenaNepostojeceJe404() throws Exception {
        upisi("/lti/registracije/999999999", true, telo("x", moodle, "k", "1", "", "", true))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Registracija nije pronađena."));
    }

    @Test
    void brisanjeVracaDvestaCetiriIUklanja() throws Exception {
        long id = napravi("klijent-4", "1").get("id").asLong();
        mvc.perform(delete("/lti/registracije/" + id).with(nastavnik)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_registracije where id = ?", Integer.class, id));
        mvc.perform(delete("/lti/registracije/" + id).with(nastavnik)).andExpect(status().isNotFound());
    }

    @Test
    void istaTrojkaJe409AliDrugiDeploymentNije() throws Exception {
        napravi("klijent-5", "1");
        upisi("/lti/registracije", false, telo("Opet", moodle, "klijent-5", "1", "", "", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("Registracija za ovaj Moodle, Client ID i Deployment ID već postoji."));
        napravi("klijent-5", "2");
    }

    @Test
    void izmenaUTrojkuDrugeRegistracijeJe409() throws Exception {
        napravi("klijent-6", "1");
        long drugi = napravi("klijent-6", "2").get("id").asLong();
        upisi("/lti/registracije/" + drugi, true, telo("Drugi", moodle, "klijent-6", "1", "", "", true))
                .andExpect(status().isConflict());
    }

    @Test
    void adreseKojeNisuHttpsSeOdbijaju() throws Exception {
        String[] losi = {"http://moodle.example.test", "ftp://moodle.example.test", "javascript:alert(1)",
                "//moodle.example.test", "https://korisnik:lozinka@moodle.example.test"};
        for (String l : losi) {
            upisi("/lti/registracije", false, telo("x", l, "k", "1", "", "", true))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason", containsString("https://")));
            upisi("/lti/registracije", false, telo("x", moodle, "k", "1", l, "", true)).andExpect(status().isBadRequest());
            upisi("/lti/registracije", false, telo("x", moodle, "k", "1", "", l, true)).andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_registracije where izdavac like ?", Integer.class,
                PREFIKS + "%"));
    }

    @Test
    void obaveznaPoljaIDuzine() throws Exception {
        upisi("/lti/registracije", false, telo("", moodle, "k", "1", "", "", true)).andExpect(status().isBadRequest());
        upisi("/lti/registracije", false, telo("x", "", "k", "1", "", "", true)).andExpect(status().isBadRequest());
        upisi("/lti/registracije", false, telo("x", moodle, "", "1", "", "", true)).andExpect(status().isBadRequest());
        upisi("/lti/registracije", false, telo("x", moodle, "k", "", "", "", true)).andExpect(status().isBadRequest());
        upisi("/lti/registracije", false, telo("n".repeat(121), moodle, "k", "1", "", "", true))
                .andExpect(status().isBadRequest());
        upisi("/lti/registracije", false, telo("x", moodle, "k".repeat(256), "1", "", "", true))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vrednostiZaMoodleIzPrvogUnosaFrontUrla() throws Exception {
        // test svojstva: gfs.front.url = http://localhost:4200 (jedan unos; lista se deli zarezom, prvi važi)
        mvc.perform(get("/lti/registracije/vrednosti").with(nastavnik)).andExpect(status().isOk())
                .andExpect(jsonPath("$.toolUrl").value("http://localhost:4200/api/public/lti/start"))
                .andExpect(jsonPath("$.redirectionUri").value("http://localhost:4200/api/public/lti/start"))
                .andExpect(jsonPath("$.initiateLoginUrl").value("http://localhost:4200/api/public/lti/prijava"))
                .andExpect(jsonPath("$.publicKeysetUrl").value("http://localhost:4200/api/public/lti/jwks"));
    }

    @Test
    void jwksJeJavanBezSesijeINemaPrivatnihDelova() throws Exception {
        MvcResult r = mvc.perform(get("/public/lti/jwks"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=300")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        JsonNode jwks = JSON.readTree(r.getResponse().getContentAsString());
        assertTrue(jwks.get("keys").size() >= 1);
        for (JsonNode k : jwks.get("keys")) {
            assertEquals("RSA", k.get("kty").asText());
            assertEquals("sig", k.get("use").asText());
            assertEquals(16, k.get("kid").asText().length());
            for (String deo : new String[]{"d", "p", "q", "dp", "dq", "qi"}) {
                assertFalse(k.has(deo), "privatni deo u JWKS-u: " + deo);
            }
        }
        String telo = r.getResponse().getContentAsString();
        // isti ključ i pri drugom zahtevu
        MvcResult drugi = mvc.perform(get("/public/lti/jwks")).andExpect(status().isOk()).andReturn();
        assertEquals(telo, drugi.getResponse().getContentAsString());
    }
}
