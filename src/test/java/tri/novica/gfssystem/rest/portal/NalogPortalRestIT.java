package tri.novica.gfssystem.rest.portal;

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
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Studentske putanje faza 2 i 3 kroz ceo bezbednosni lanac (MockMvc sa filterima): bez sesije 401, tuđ predmet 404 sa
 * istom porukom, nastavničke putanje (objava, panel, statistika) ne prihvataju studentsku sesiju, upis traži poreklo, a
 * {@code ja} nosi grupu i generaciju.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NalogPortalRestIT {

    @Autowired MockMvc mvc;
    @Autowired StudentskaPrijava studentskaPrijava;
    @Autowired JdbcTemplate jdbc;

    CasPodaci d;
    Long grupa, drugaGrupa, ana, bora;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        grupa = d.grupa(2026);
        drugaGrupa = d.grupa(2026);
        d.predavanje(grupa, LocalDate.now());
        ana = d.student(grupa, "Ana");
        bora = d.student(drugaGrupa, "Bora");
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
    }

    Cookie studentskiKolacic(Long studentId) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        studentskaPrijava.prijavi(studentId, NacinPrijave.KOD, new MockHttpServletRequest(), res);
        String h = res.getHeader("Set-Cookie");
        assertNotNull(h);
        return new Cookie(Kolacici.STUDENT, h.substring(h.indexOf('=') + 1, h.indexOf(';')));
    }

    @Test
    void bezSesije401() throws Exception {
        mvc.perform(get("/public/nalog/predmeti")).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/nalog/prezentacije/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/public/nalog/vezbanje").header("Sec-Fetch-Site", "same-origin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipId\":1}")).andExpect(status().isUnauthorized());
    }

    @Test
    void sopstveniPredmetDaTudjNe() throws Exception {
        Cookie a = studentskiKolacic(ana);
        mvc.perform(get("/public/nalog/predmeti").cookie(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(d.predmetId))
                .andExpect(jsonPath("$[0].predavanja").value(1));
        mvc.perform(get("/public/nalog/predmeti/{id}/ocena", d.predmetId).cookie(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objavljeno").value(false))
                .andExpect(jsonPath("$.ocena").doesNotExist());
        Cookie b = studentskiKolacic(bora);
        mvc.perform(get("/public/nalog/predmeti").cookie(b)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        for (String put : new String[]{"prisustvo", "vezbanje", "moji-zadaci", "prezentacije", "ocena"}) {
            mvc.perform(get("/public/nalog/predmeti/{id}/" + put, d.predmetId).cookie(b))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.reason").value("Nije pronađeno."));
        }
        mvc.perform(get("/public/nalog/prezentacije/{id}", 999_999_999L).cookie(a))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Nije pronađeno."));
        mvc.perform(post("/public/nalog/vezbanje/{id}/resenje", 999_999_999L).header("Sec-Fetch-Site", "same-origin").cookie(a))
                .andExpect(status().isNotFound());
    }

    @Test
    void upisTraziPoreklo() throws Exception {
        mvc.perform(post("/public/nalog/vezbanje").cookie(studentskiKolacic(ana)).header("Sec-Fetch-Site", "cross-site")
                        .header("Origin", "https://zlo.example").contentType(MediaType.APPLICATION_JSON).content("{\"tipId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nastavnickePutanjeNePrihvatajuStudenta() throws Exception {
        Cookie a = studentskiKolacic(ana);
        mvc.perform(get("/ocenjivanje/predmet/{p}/objava", d.predmetId).param("grupa", grupa.toString()).cookie(a))
                .andExpect(status().isForbidden());
        mvc.perform(put("/ocenjivanje/predmet/{p}/objava/{g}", d.predmetId, grupa).header("Sec-Fetch-Site", "same-origin").cookie(a))
                .andExpect(status().isForbidden());
        mvc.perform(get("/banka/grupe/1/otvoreno").cookie(a)).andExpect(status().isForbidden());
        mvc.perform(get("/predmeti/{p}/vezbanje/statistika", d.predmetId).cookie(a)).andExpect(status().isForbidden());
        mvc.perform(get("/ocenjivanje/predmet/{p}/objava", d.predmetId).param("grupa", grupa.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void podesavanjaNaloga() throws Exception {
        Cookie a = studentskiKolacic(ana);
        mvc.perform(get("/public/nalog/podesavanja")).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/nalog/podesavanja").cookie(a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mejlOtkazanCas").value(true));
        mvc.perform(put("/public/nalog/podesavanja").cookie(a).header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mejlOtkazanCas\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mejlOtkazanCas").value(false));
        mvc.perform(get("/public/nalog/podesavanja").cookie(a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mejlOtkazanCas").value(false));
        mvc.perform(put("/public/nalog/podesavanja").cookie(a).header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/public/nalog/podesavanja").cookie(a).header("Sec-Fetch-Site", "cross-site")
                        .header("Origin", "https://zlo.example").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mejlOtkazanCas\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/public/nalog/podesavanja").cookie(studentskiKolacic(bora)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mejlOtkazanCas").value(true));
    }

    @Test
    void jaNosiGrupuIGeneraciju() throws Exception {
        mvc.perform(get("/public/nalog/ja").cookie(studentskiKolacic(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupa").value("C-" + d.oznaka + "-1"))
                .andExpect(jsonPath("$.generacija").value(2026));
    }
}
