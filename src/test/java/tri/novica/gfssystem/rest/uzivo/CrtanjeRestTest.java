package tri.novica.gfssystem.rest.uzivo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.PozadinaCrteza;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.CrtanjeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Oblik odgovora iz ugovora (plan crtanja, "Ugovor") koji front čita, 204 table i greške servisa. */
@WebMvcTest(CrtanjeRest.class)
class CrtanjeRestTest {

    static final ElementCrteza LINIJA = new ElementCrteza(TipElementa.STRELICA, "#C62828", 3.0, List.of(1.0, 2.0, 3.5, 4.0));

    @Autowired MockMvc mvc;
    @MockitoBean CrtanjeService service;

    @Test
    void stranaIUpis() throws Exception {
        when(service.strana(5L, 9L, PozadinaCrteza.BELA)).thenReturn(new StranaCrteza(List.of(LINIJA), 3));
        mvc.perform(get("/izvodjenja/5/crtezi/strana").param("slajdId", "9").param("pozadina", "BELA"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.verzija").value(3))
           .andExpect(jsonPath("$.elementi[0].tip").value("STRELICA"))
           .andExpect(jsonPath("$.elementi[0].boja").value("#C62828"))
           .andExpect(jsonPath("$.elementi[0].debljina").value(3.0))
           .andExpect(jsonPath("$.elementi[0].tacke[2]").value(3.5));

        when(service.upisi(eq(5L), eq(9L), eq(PozadinaCrteza.SLAJD), any())).thenReturn(new VerzijaCrteza(4));
        String telo = "{\"elementi\":[{\"tip\":\"KRUG\",\"boja\":\"#000000\",\"debljina\":6,\"tacke\":[1,2,3,4]}]}";
        mvc.perform(put("/izvodjenja/5/crtezi/strana").param("slajdId", "9").param("pozadina", "SLAJD")
                        .contentType(MediaType.APPLICATION_JSON).content(telo))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.verzija").value(4));
        verify(service).upisi(eq(5L), eq(9L), eq(PozadinaCrteza.SLAJD),
                argThat((JsonNode n) -> n.get("elementi").get(0).get("tip").asString().equals("KRUG")));
    }

    @Test
    void pregledIRanije() throws Exception {
        when(service.sve(5L)).thenReturn(List.of(new CrtezIzvodjenjaInfo(null, null, PozadinaCrteza.SLAJD, List.of(LINIJA), 2,
                LocalDateTime.of(2026, 11, 23, 10, 15))));
        mvc.perform(get("/izvodjenja/5/crtezi"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].slajdId").value(nullValue()))
           .andExpect(jsonPath("$[0].slajdBroj").value(nullValue()))
           .andExpect(jsonPath("$[0].pozadina").value("SLAJD"))
           .andExpect(jsonPath("$[0].verzija").value(2))
           .andExpect(jsonPath("$[0].izmenjeno").value("2026-11-23T10:15:00"));

        when(service.ranije(5L, 9L)).thenReturn(List.of(new RanijiCrtez(3L, LocalDateTime.of(2026, 11, 16, 9, 0), null,
                PozadinaCrteza.BELA, List.of(LINIJA))));
        mvc.perform(get("/izvodjenja/5/crtezi/ranije").param("slajdId", "9"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].izvodjenjeId").value(3))
           .andExpect(jsonPath("$[0].pocetak").value("2026-11-16T09:00:00"))
           .andExpect(jsonPath("$[0].grupa").value(nullValue()))
           .andExpect(jsonPath("$[0].elementi[0].tip").value("STRELICA"));
    }

    @Test
    void tablaVraca204KadNemaNovog() throws Exception {
        when(service.tabla(7L, 2)).thenReturn(Optional.empty());
        mvc.perform(get("/predavanja/7/tabla").param("posle", "2"))
           .andExpect(status().isNoContent())
           .andExpect(content().string(""));
        when(service.tabla(7L, null)).thenReturn(Optional.of(new StranaCrteza(List.of(), 0)));
        mvc.perform(get("/predavanja/7/tabla"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.verzija").value(0))
           .andExpect(jsonPath("$.elementi").isEmpty());
        when(service.upisiTablu(eq(7L), any())).thenReturn(new VerzijaCrteza(1));
        mvc.perform(put("/predavanja/7/tabla").contentType(MediaType.APPLICATION_JSON).content("{\"elementi\":[]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.verzija").value(1));
    }

    @Test
    void greske() throws Exception {
        mvc.perform(get("/izvodjenja/5/crtezi/strana").param("slajdId", "9").param("pozadina", "ZUTA"))
           .andExpect(status().isBadRequest());
        when(service.upisi(eq(5L), eq(9L), eq(PozadinaCrteza.SLAJD), any()))
                .thenThrow(new SystemException("Izvođenje je završeno.", HttpStatus.GONE));
        mvc.perform(put("/izvodjenja/5/crtezi/strana").param("slajdId", "9").param("pozadina", "SLAJD")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"elementi\":[]}"))
           .andExpect(status().isGone())
           .andExpect(jsonPath("$.reason").value("Izvođenje je završeno."));
    }
}
