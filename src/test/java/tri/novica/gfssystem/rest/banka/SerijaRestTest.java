package tri.novica.gfssystem.rest.banka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.banka.PonistavanjeInfo;
import tri.novica.gfssystem.dto.banka.SerijaInfo;
import tri.novica.gfssystem.dto.banka.StavkaPonistavanja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.SerijaService;
import tri.novica.gfssystem.utility.Serija;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code GET /banka/serije} i {@code POST /banka/serije/{serija}/odbaci}: statusi 200, odbacivanje je poništavanje sa
 * potvrdom, bez {@code predmetId} servis dobija {@code null} (njegova poruka, ne Spring-ova), 400 poruke servisa.
 */
@WebMvcTest(SerijaRest.class)
class SerijaRestTest {

    @Autowired MockMvc mvc;
    @MockitoBean SerijaService service;

    @Test
    void serijeVracaju200() throws Exception {
        when(service.serije(7L)).thenReturn(List.of(
                new SerijaInfo("uvoz-kol1-2024", 3, 1, 2, 0, 4, LocalDateTime.of(2026, 10, 18, 12, 30)),
                new SerijaInfo("uvoz-kol2-2024", 0, 0, 1, 1, 0, LocalDateTime.of(2026, 10, 17, 9, 0))));

        mvc.perform(get("/banka/serije").param("predmetId", "7"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[0].serija").value("uvoz-kol1-2024"))
           .andExpect(jsonPath("$[0].nacrta").value(3))
           .andExpect(jsonPath("$[0].odobrenih").value(1))
           .andExpect(jsonPath("$[0].grupa").value(2))
           .andExpect(jsonPath("$[0].prezentacija").value(0))
           .andExpect(jsonPath("$[0].slajdova").value(4))
           .andExpect(jsonPath("$[0].poslednja").value("2026-10-18T12:30:00"))
           .andExpect(jsonPath("$[1].serija").value("uvoz-kol2-2024"));
    }

    @Test
    void bezPredmetaJePorukaServisa() throws Exception {
        when(service.serije(null)).thenThrow(new SystemException("Predmet je obavezan.", HttpStatus.BAD_REQUEST));
        mvc.perform(get("/banka/serije"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Predmet je obavezan."))
           .andExpect(jsonPath("$.time").exists());
        verify(service).serije(null);
    }

    @Test
    void neispravanPredmetJe400() throws Exception {
        mvc.perform(get("/banka/serije").param("predmetId", "abc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: predmetId."));
        verifyNoInteractions(service);
    }

    @Test
    void odbaciJePonistavanjeSaPotvrdom() throws Exception {
        when(service.ponisti("uvoz-kol1-2024", true)).thenReturn(new PonistavanjeInfo(true,
                List.of(new StavkaPonistavanja(StavkaPonistavanja.VARIJANTA, 11L, "Konverzije, varijanta 2", null),
                        new StavkaPonistavanja(StavkaPonistavanja.GRUPA, 5L, "Konverzije", null)),
                List.of(new StavkaPonistavanja(StavkaPonistavanja.VARIJANTA, 12L, "Konverzije, varijanta 3",
                        "Odobrena ili nije Klodov nacrt."))));

        mvc.perform(post("/banka/serije/uvoz-kol1-2024/odbaci"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.potvrdjeno").value(true))
           .andExpect(jsonPath("$.obrisano.length()").value(2))
           .andExpect(jsonPath("$.obrisano[0].vrsta").value("VARIJANTA"))
           .andExpect(jsonPath("$.obrisano[0].id").value(11))
           .andExpect(jsonPath("$.obrisano[0].naziv").value("Konverzije, varijanta 2"))
           .andExpect(jsonPath("$.obrisano[0].razlog").value(nullValue()))
           .andExpect(jsonPath("$.obrisano[1].vrsta").value("GRUPA"))
           .andExpect(jsonPath("$.preskoceno[0].id").value(12))
           .andExpect(jsonPath("$.preskoceno[0].razlog").value("Odobrena ili nije Klodov nacrt."));
        verify(service).ponisti("uvoz-kol1-2024", true);
        verifyNoMoreInteractions(service);
    }

    @Test
    void neispravnaSerijaJePorukaServisa() throws Exception {
        when(service.ponisti(eq("Uvoz-1"), anyBoolean())).thenThrow(new SystemException(Serija.PORUKA, HttpStatus.BAD_REQUEST));
        mvc.perform(post("/banka/serije/Uvoz-1/odbaci"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value(Serija.PORUKA));
    }
}
