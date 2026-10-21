package tri.novica.gfssystem.rest.banka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaCmd;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaInfo;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.IskljuceneTemeService;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isključene teme predmeta: statusi 200/201/204, REST dodaje samo ručne teme (RUCNO, bez serije), 404 poruke servisa. */
@WebMvcTest(IskljuceneTemeRest.class)
class IskljuceneTemeRestTest {

    static final LocalDateTime KREIRANO = LocalDateTime.of(2026, 10, 18, 12, 0);
    static final IskljucenaTemaInfo NAD_GRUPOM = new IskljucenaTemaInfo(4L, 7L, "Razlomljeni (decimalni) brojevi", 5L,
            List.of(new GrupaPutanja(3L, "Brojni sistemi"), new GrupaPutanja(5L, "Razlomljeni brojevi")),
            IzvorVarijante.CLAUDE, "uvoz-1", KREIRANO);
    static final IskljucenaTemaInfo BEZ_GRUPE = new IskljucenaTemaInfo(6L, 7L, "Pokretni zarez", null, List.of(),
            IzvorVarijante.RUCNO, null, KREIRANO);

    @Autowired MockMvc mvc;
    @MockitoBean IskljuceneTemeService service;

    @Test
    void listaVraca200() throws Exception {
        when(service.lista(7L)).thenReturn(List.of(BEZ_GRUPE, NAD_GRUPOM));
        mvc.perform(get("/predmeti/7/iskljucene-teme"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$[0].id").value(6))
           .andExpect(jsonPath("$[0].grupaId").value(nullValue()))
           .andExpect(jsonPath("$[0].putanja").isEmpty())
           .andExpect(jsonPath("$[0].izvor").value("RUCNO"))
           .andExpect(jsonPath("$[0].serija").value(nullValue()))
           .andExpect(jsonPath("$[1].predmetId").value(7))
           .andExpect(jsonPath("$[1].opis").value("Razlomljeni (decimalni) brojevi"))
           .andExpect(jsonPath("$[1].grupaId").value(5))
           .andExpect(jsonPath("$[1].putanja[1].naziv").value("Razlomljeni brojevi"))
           .andExpect(jsonPath("$[1].izvor").value("CLAUDE"))
           .andExpect(jsonPath("$[1].serija").value("uvoz-1"))
           .andExpect(jsonPath("$[1].kreirano").value("2026-10-18T12:00:00"));
    }

    @Test
    void dodajVraca201IUvekJeRucno() throws Exception {
        when(service.dodaj(eq(7L), any(), any(), any())).thenReturn(BEZ_GRUPE);
        mvc.perform(post("/predmeti/7/iskljucene-teme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opis\":\"Pokretni zarez\",\"grupaId\":5}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(6))
           .andExpect(jsonPath("$.opis").value("Pokretni zarez"));
        verify(service).dodaj(7L, new IskljucenaTemaCmd("Pokretni zarez", 5L), IzvorVarijante.RUCNO, null);

        mvc.perform(post("/predmeti/7/iskljucene-teme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opis\":\"Pokretni zarez\"}"))
           .andExpect(status().isCreated());
        verify(service).dodaj(7L, new IskljucenaTemaCmd("Pokretni zarez", null), IzvorVarijante.RUCNO, null);
    }

    @Test
    void obrisiVraca204() throws Exception {
        mvc.perform(delete("/iskljucene-teme/4"))
           .andExpect(status().isNoContent())
           .andExpect(content().string(""));
        verify(service).obrisi(4L);
    }

    @Test
    void porukeServisaIdu404I409() throws Exception {
        when(service.lista(9L)).thenThrow(new SystemException("Predmet nije pronađen.", HttpStatus.NOT_FOUND));
        mvc.perform(get("/predmeti/9/iskljucene-teme"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Predmet nije pronađen."))
           .andExpect(jsonPath("$.time").exists());

        doThrow(new SystemException("Isključena tema nije pronađena.", HttpStatus.NOT_FOUND)).when(service).obrisi(8L);
        mvc.perform(delete("/iskljucene-teme/8"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Isključena tema nije pronađena."));

        when(service.dodaj(eq(7L), any(), any(), any()))
                .thenThrow(new SystemException("Ova grupa je već isključena.", HttpStatus.CONFLICT));
        mvc.perform(post("/predmeti/7/iskljucene-teme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opis\":\"Razlomljeni brojevi\",\"grupaId\":5}"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Ova grupa je već isključena."));
    }

    @Test
    void neispravnoTeloJe400() throws Exception {
        mvc.perform(post("/predmeti/7/iskljucene-teme").contentType(MediaType.APPLICATION_JSON).content("{\"opis\":"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        mvc.perform(post("/predmeti/7/iskljucene-teme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opis\":\"x\",\"grupaId\":\"pet\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        verifyNoInteractions(service);
    }
}
