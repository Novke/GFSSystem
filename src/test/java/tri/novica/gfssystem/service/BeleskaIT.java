package tri.novica.gfssystem.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Beleške kroz HTTP nad pravom MySQL bazom (Flyway V4, {@code ddl-auto=validate}); svaki test se vraća unazad. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BeleskaIT {

    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    Student student;

    @BeforeEach
    void seed() {
        Grupa grupa = new Grupa();
        grupa.setNaziv("BL-A");
        grupa.setGodinaUpisa(2025);
        em.persist(grupa);
        student = new Student();
        student.setIme("Ana");
        student.setPrezime("Anić");
        student.setIndeks("BL1");
        student.setGodina(2025);
        student.setGrupa(grupa);
        em.persist(student);
        em.flush();
    }

    private static String telo(String tekst) {
        return "{\"tekst\":" + (tekst == null ? "null" : "\"" + tekst + "\"") + "}";
    }

    @Test
    void krugKreiranjeListaIzmenaBrisanje() throws Exception {
        String odgovor = mvc.perform(post("/studenti/{id}/beleske", student.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(telo("Prva")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tekst").value("Prva"))
                .andExpect(jsonPath("$.kreirano").isNotEmpty())
                .andExpect(jsonPath("$.izmenjeno").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(odgovor.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(post("/studenti/{id}/beleske", student.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(telo("Druga")))
                .andExpect(status().isCreated());

        mvc.perform(get("/studenti/{id}/beleske", student.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tekst").value("Druga"))
                .andExpect(jsonPath("$[1].tekst").value("Prva"));

        mvc.perform(put("/beleske/{id}", id).contentType(MediaType.APPLICATION_JSON).content(telo("Izmenjena")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tekst").value("Izmenjena"))
                .andExpect(jsonPath("$.izmenjeno").isNotEmpty());

        mvc.perform(delete("/beleske/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/studenti/{id}/beleske", student.getId()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void praznTekstJe400() throws Exception {
        mvc.perform(post("/studenti/{id}/beleske", student.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(telo("  ")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/studenti/{id}/beleske", student.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(telo(null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nepostojeciStudentIBeleskaSu404() throws Exception {
        mvc.perform(get("/studenti/{id}/beleske", 999999999L)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("Student ne postoji! ID = 999999999"));
        mvc.perform(post("/studenti/{id}/beleske", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON).content(telo("x")))
                .andExpect(status().isNotFound());
        mvc.perform(put("/beleske/{id}", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON).content(telo("x")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/beleske/{id}", 999999999L)).andExpect(status().isNotFound());
    }
}
