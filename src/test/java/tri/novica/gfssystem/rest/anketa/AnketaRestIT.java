package tri.novica.gfssystem.rest.anketa;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Brza anketa kroz ceo bezbednosni lanac (MockMvc sa filterima) nad pravom bazom (spec brze ankete 5 i 9): nastavničke
 * putanje (ADMIN), studentske pod {@code /public/nalog/ankete} (STUDENT), provera porekla, {@code ankete} u stanju časa i da
 * nijedan nastavnički odgovor ne nosi studenta (id, ime) ni pojedinačan glas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnketaRestIT {

    @Autowired MockMvc mvc;
    @Autowired StudentskaPrijava studentskaPrijava;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    CasPodaci d;
    Long grupa;
    Long predavanje;
    Long ana;
    Long bora;
    RequestPostProcessor nastavnik;
    final List<Long> sabloni = new ArrayList<>();
    final List<Long> folderi = new ArrayList<>();

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now(clock));
        ana = d.student(grupa, "Anastasija");
        bora = d.student(grupa, "Borislav");
        jdbc.update("insert into cekiranja (predavanje_id, student_id, vreme) values (?, ?, ?)", predavanje, ana,
                Timestamp.valueOf(LocalDateTime.now(clock).minusMinutes(5)));
        nastavnik = PrijavaTestUtil.kaoNastavnik(PrijavaTestUtil.nastavnickiKolacic(nastavnici));
    }

    @AfterEach
    void tearDown() {
        for (Long id : sabloni) jdbc.update("delete from ankete_sabloni where id = ?", id);
        for (Long id : folderi) jdbc.update("delete from ankete_folderi where id = ?", id);
        d.obrisi();
    }

    Cookie studentskiKolacic(Long studentId) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        studentskaPrijava.prijavi(studentId, NacinPrijave.KOD, new MockHttpServletRequest(), res);
        String h = res.getHeader("Set-Cookie");
        assertNotNull(h);
        return new Cookie(Kolacici.STUDENT, h.substring(h.indexOf('=') + 1, h.indexOf(';')));
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String telo) {
        return b.contentType(MediaType.APPLICATION_JSON).content(telo);
    }

    String telo(MockHttpServletRequestBuilder b, int status) throws Exception {
        return mvc.perform(b).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }

    /** Nastavnički JSON ne sme da nosi studenta: ni ključ {@code studentId}, ni ime ili prezime, ni {@code mojGlas}. */
    void bezStudenata(String json) {
        for (String zabranjeno : List.of("studentId", "student_id", "Anastasija", "Borislav", "Čekić", "mojGlas", "indeks")) {
            assertFalse(json.contains(zabranjeno), zabranjeno + " u " + json);
        }
    }

    @Test
    void ceoLanacNastavnikIStudent() throws Exception {
        // šablon i folder
        String f = telo(json(post("/ankete/folderi"), "{\"naziv\":\"Tok časa\"}").with(nastavnik), 201);
        Long folderId = ((Number) JsonPath.read(f, "$.id")).longValue();
        folderi.add(folderId);
        String s = telo(json(post("/ankete/sabloni"), "{\"folderId\":" + folderId + ",\"pitanje\":\"Da li vam treba pauza?\","
                + "\"opcije\":[\"Da, 10 min\",\"Da, 15 min\",\"Ne\"],\"viseOdgovora\":false,\"publika\":\"NA_CASU\","
                + "\"trajanjeSekunde\":60,\"precica\":true,\"ikonica\":\"☕\",\"boja\":\"PLAVA\",\"kratakNaziv\":\"Pauza?\","
                + "\"pokreniOdmah\":true}").with(nastavnik), 201);
        Long sablonId = ((Number) JsonPath.read(s, "$.id")).longValue();
        sabloni.add(sablonId);
        assertEquals("PLAVA", JsonPath.read(s, "$.boja"));
        assertEquals(Boolean.TRUE, JsonPath.read(s, "$.pokreniOdmah"));
        mvc.perform(get("/ankete/sabloni").with(nastavnik)).andExpect(status().isOk())
                .andExpect(jsonPath("$.folderi[?(@.id == " + folderId + ")].brojSablona").value(1))
                .andExpect(jsonPath("$.sabloni[?(@.id == " + sablonId + ")].precicaRb").isNotEmpty());

        // pokretanje
        String a = telo(json(post("/ankete"), "{\"sablonId\":" + sablonId + ",\"pitanje\":\"Da li vam treba pauza?\","
                + "\"opcije\":[\"Da, 10 min\",\"Da, 15 min\",\"Ne\"],\"viseOdgovora\":false,\"publika\":\"NA_CASU\","
                + "\"predavanjeId\":" + predavanje + ",\"trajanjeSekunde\":600}").with(nastavnik), 201);
        Long anketaId = ((Number) JsonPath.read(a, "$.id")).longValue();
        bezStudenata(a);
        assertEquals(Boolean.TRUE, JsonPath.read(a, "$.otvorena"));
        assertEquals(1, (int) JsonPath.read(a, "$.brojMogucih"));
        assertEquals("NASTAVNIK", JsonPath.read(a, "$.izvor"));
        assertNotNull(JsonPath.read(a, "$.rokMs"));
        assertNotNull(JsonPath.read(a, "$.sadaMs"));
        assertEquals(List.of(0, 0, 0), JsonPath.read(a, "$.glasovi"));

        // student: stanje časa i lista
        Cookie anin = studentskiKolacic(ana);
        mvc.perform(get("/public/nalog/cas").cookie(anin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.predavanje.id").value(predavanje))
                .andExpect(jsonPath("$.ankete", hasSize(1)))
                .andExpect(jsonPath("$.ankete[0].id").value(anketaId))
                .andExpect(jsonPath("$.ankete[0].mojGlas").doesNotExist())
                .andExpect(jsonPath("$.ankete[0].rezultat").doesNotExist())
                .andExpect(jsonPath("$.ankete[0].brojMogucih").doesNotExist());
        Cookie borin = studentskiKolacic(bora);
        mvc.perform(get("/public/nalog/cas").cookie(borin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ankete", hasSize(0)));

        // glas: poreklo, ko sme, promena
        mvc.perform(json(post("/public/nalog/ankete/" + anketaId + "/glas"), "{\"opcije\":[1]}").cookie(anin)
                .header("Sec-Fetch-Site", "cross-site")).andExpect(status().isForbidden());
        mvc.perform(json(post("/public/nalog/ankete/" + anketaId + "/glas"), "{\"opcije\":[1]}")
                .header("Sec-Fetch-Site", "same-origin")).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/public/nalog/ankete/" + anketaId + "/glas"), "{\"opcije\":[1]}").cookie(borin)
                        .header("Sec-Fetch-Site", "same-origin")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("Ova anketa nije za tebe."));
        mvc.perform(json(post("/public/nalog/ankete/" + anketaId + "/glas"), "{\"opcije\":[0,1]}").cookie(anin)
                        .header("Sec-Fetch-Site", "same-origin")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Izaberi jedan odgovor."));
        mvc.perform(json(post("/public/nalog/ankete/" + anketaId + "/glas"), "{\"opcije\":[0]}").cookie(anin)
                .header("Sec-Fetch-Site", "same-origin")).andExpect(status().isOk());
        mvc.perform(json(post("/public/nalog/ankete/" + anketaId + "/glas"), "{\"opcije\":[1]}").cookie(anin)
                        .header("Sec-Fetch-Site", "same-origin")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mojGlas[0]").value(1))
                .andExpect(jsonPath("$.otvorena").value(true));
        mvc.perform(get("/public/nalog/ankete").cookie(anin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mojGlas[0]").value(1));
        mvc.perform(get("/public/nalog/ankete")).andExpect(status().isUnauthorized());

        // nastavnik: aktivne, detalji, rezultat, zatvaranje, istorija
        String akt = telo(get("/ankete/aktivne").param("predavanjeId", predavanje.toString()).with(nastavnik), 200);
        bezStudenata(akt);
        assertEquals(List.of(0, 1, 0), JsonPath.read(akt, "$[0].glasovi"));
        String r = telo(json(put("/ankete/" + anketaId + "/rezultat"), "{\"prikazan\":true}").with(nastavnik), 200);
        bezStudenata(r);
        assertEquals(Boolean.TRUE, JsonPath.read(r, "$.rezultatPrikazan"));
        mvc.perform(get("/public/nalog/ankete").cookie(anin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rezultat.glasovi[1]").value(1))
                .andExpect(jsonPath("$[0].rezultat.brojGlasalo").value(1));
        String z = telo(post("/ankete/" + anketaId + "/zatvori").with(nastavnik), 200);
        bezStudenata(z);
        assertEquals(Boolean.FALSE, JsonPath.read(z, "$.otvorena"));
        assertNotNull(JsonPath.read(z, "$.zatvoreno"));
        mvc.perform(json(post("/public/nalog/ankete/" + anketaId + "/glas"), "{\"opcije\":[0]}").cookie(anin)
                        .header("Sec-Fetch-Site", "same-origin")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("Anketa je zatvorena."));
        String ist = telo(get("/ankete").param("predavanjeId", predavanje.toString()).param("page", "0")
                .param("size", "20").with(nastavnik), 200);
        bezStudenata(ist);
        assertEquals(anketaId.intValue(), (int) JsonPath.read(ist, "$.content[0].id"));
        assertEquals("Da, 15 min", JsonPath.read(ist, "$.content[0].pobednik"));
        assertEquals(1, (int) JsonPath.read(ist, "$.page.totalElements"));
        mvc.perform(get("/ankete").param("sort", "pitanje,asc").with(nastavnik)).andExpect(status().isBadRequest());
        bezStudenata(telo(get("/ankete/" + anketaId).with(nastavnik), 200));

        mvc.perform(delete("/ankete/" + anketaId).with(nastavnik)).andExpect(status().isNoContent());
        mvc.perform(get("/ankete/" + anketaId).with(nastavnik)).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("select count(*) from ankete_glasovi where anketa_id = ?", Integer.class,
                anketaId), "glasovi kaskadno");
    }

    @Test
    void sabloniFolderiIPreciceKrozRest() throws Exception {
        Long f1 = ((Number) JsonPath.read(telo(json(post("/ankete/folderi"), "{\"naziv\":\"A\"}").with(nastavnik), 201),
                "$.id")).longValue();
        Long f2 = ((Number) JsonPath.read(telo(json(post("/ankete/folderi"), "{\"naziv\":\"B\"}").with(nastavnik), 201),
                "$.id")).longValue();
        folderi.addAll(List.of(f1, f2));
        mvc.perform(json(post("/ankete/folderi"), "{\"naziv\":\"a\"}").with(nastavnik)).andExpect(status().isConflict());
        mvc.perform(json(put("/ankete/folderi/" + f1), "{\"naziv\":\"A2\"}").with(nastavnik)).andExpect(status().isOk())
                .andExpect(jsonPath("$.naziv").value("A2"));
        Long s1 = ((Number) JsonPath.read(telo(json(post("/ankete/sabloni"), "{\"folderId\":" + f1 + ",\"pitanje\":\"P1\","
                + "\"opcije\":[\"Da\",\"Ne\"]}").with(nastavnik), 201), "$.id")).longValue();
        Long s2 = ((Number) JsonPath.read(telo(json(post("/ankete/sabloni"), "{\"pitanje\":\"P2\",\"opcije\":[\"Da\",\"Ne\"],"
                + "\"publika\":\"GRUPA\",\"rokSati\":24}").with(nastavnik), 201), "$.id")).longValue();
        sabloni.addAll(List.of(s1, s2));
        mvc.perform(json(put("/ankete/sabloni/" + s2), "{\"pitanje\":\"P2\",\"opcije\":[\"Da\"]}").with(nastavnik))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Anketa mora imati od 2 do 10 odgovora."));
        mvc.perform(json(put("/ankete/precice"), "{\"ids\":[" + s2 + "," + s1 + "]}").with(nastavnik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sabloni[0].id").value(s2))
                .andExpect(jsonPath("$.sabloni[1].id").value(s1))
                .andExpect(jsonPath("$.sabloni[1].precicaRb").value(2));
        mvc.perform(json(put("/ankete/precice"), "{}").with(nastavnik)).andExpect(status().isBadRequest());
        String svi = telo(get("/ankete/sabloni").with(nastavnik), 200);
        List<Integer> ids = JsonPath.read(svi, "$.folderi[*].id");
        List<Long> redosled = new ArrayList<>(ids.stream().map(Integer::longValue).toList());
        java.util.Collections.reverse(redosled);
        mvc.perform(json(put("/ankete/folderi/redosled"), "{\"ids\":" + redosled + "}").with(nastavnik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderi[0].id").value(redosled.getFirst()));
        mvc.perform(delete("/ankete/folderi/" + f1).with(nastavnik)).andExpect(status().isNoContent());
        assertNull(jdbc.queryForObject("select folder_id from ankete_sabloni where id = ?", Long.class, s1),
                "šablon ostaje bez foldera");
        mvc.perform(delete("/ankete/sabloni/" + s1).with(nastavnik)).andExpect(status().isNoContent());
        mvc.perform(delete("/ankete/sabloni/" + s1).with(nastavnik)).andExpect(status().isNotFound());
    }

    @Test
    void anketaZaGrupuIGreskeIstorije() throws Exception {
        LocalDateTime rok = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.SECONDS);
        String a = telo(json(post("/ankete"), "{\"pitanje\":\"Kolokvijum?\",\"opcije\":[\"12.12.\",\"19.12.\"],"
                + "\"publika\":\"GRUPA\",\"grupaId\":" + grupa + ",\"rok\":\"" + rok + "\"}").with(nastavnik), 201);
        bezStudenata(a);
        assertEquals(2, (int) JsonPath.read(a, "$.brojMogucih"));
        Long id = ((Number) JsonPath.read(a, "$.id")).longValue();
        Cookie borin = studentskiKolacic(bora);
        mvc.perform(get("/public/nalog/ankete").cookie(borin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].publika").value("GRUPA"))
                .andExpect(jsonPath("$[0].rokMs").isNumber());
        mvc.perform(get("/public/nalog/cas").cookie(borin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.predavanje").doesNotExist())
                .andExpect(jsonPath("$.ankete[0].id").value(id));
        mvc.perform(json(post("/ankete"), "{\"pitanje\":\"K?\",\"opcije\":[\"a\",\"b\"],\"publika\":\"GRUPA\",\"grupaId\":"
                        + grupa + "}").with(nastavnik)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Izaberi rok."));
        mvc.perform(get("/ankete/aktivne").with(nastavnik)).andExpect(status().isBadRequest());
        mvc.perform(get("/ankete/aktivne").param("grupaId", grupa.toString()).with(nastavnik)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void nastavnickePutanjeTrazeSesijuIPoreklo() throws Exception {
        mvc.perform(get("/ankete/sabloni")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ankete")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ankete/aktivne").param("grupaId", grupa.toString())).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/ankete"), "{}").header("Origin", PrijavaTestUtil.ORIGIN))
                .andExpect(status().isUnauthorized());
        Cookie anin = studentskiKolacic(ana);
        mvc.perform(get("/ankete/sabloni").cookie(anin)).andExpect(status().isForbidden());
        String kolacic = PrijavaTestUtil.nastavnickiKolacic(nastavnici);
        Cookie sesija = new Cookie(kolacic.substring(0, kolacic.indexOf('=')), kolacic.substring(kolacic.indexOf('=') + 1));
        mvc.perform(json(post("/ankete/folderi"), "{\"naziv\":\"X\"}").cookie(sesija).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("select count(*) from ankete_folderi where naziv = 'X'", Integer.class));
    }
}
