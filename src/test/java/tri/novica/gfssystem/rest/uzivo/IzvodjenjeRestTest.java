package tri.novica.gfssystem.rest.uzivo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.StanjeService;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IzvodjenjeRest.class)
class IzvodjenjeRestTest {

    static final IzvodjenjeInfo INFO = new IzvodjenjeInfo(5L, new PrezentacijaKratko(1L, "Uvod", 7L), "123456",
            StatusIzvodjenja.AKTIVNO, false, null, null, LocalDateTime.of(2026, 10, 7, 12, 0), null, 2, 1);
    static final NastavnickoStanje STANJE = new NastavnickoStanje(INFO, 4, 1_759_831_200_000L, Prikaz.SLAJD, 0, 0, 0, 3,
            null, null, Faza.OTVORENO, new RundaInfo(101L, 1, 1_759_831_220_000L, null, true), false, false, false,
            Ekran.NORMALAN, false, TelefonPrikaz.DUGMAD, true, false, null, 1, 1,
            List.of(new UcesnikStanje(31L, "Ana", 0, true, true)), List.of(new RangStavka(1, 31L, "Ana", 0)));

    @Autowired MockMvc mvc;
    @MockitoBean IzvodjenjeService service;
    @MockitoBean StanjeService stanjeService;

    @Test
    void komandaVracaStanje() throws Exception {
        when(service.komanda(eq(5L), any())).thenReturn(STANJE);
        mvc.perform(post("/izvodjenja/5/komande").contentType(MediaType.APPLICATION_JSON).content("{\"tip\":\"SLEDECI\"}"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.verzija").value(4))
           .andExpect(jsonPath("$.izvodjenje.kod").value("123456"))
           .andExpect(jsonPath("$.izvodjenje.prezentacija.predmetId").value(7))
           .andExpect(jsonPath("$.faza").value("OTVORENO"))
           .andExpect(jsonPath("$.runda.rokMs").value(1_759_831_220_000L))
           .andExpect(jsonPath("$.runda.preostaloMs").value(nullValue()))
           .andExpect(jsonPath("$.runda.tajmerRadi").value(true))
           .andExpect(jsonPath("$.trenutniSlajd").value(nullValue()))
           .andExpect(jsonPath("$.ucesnici[0].odgovorio").value(true))
           .andExpect(jsonPath("$.rangLista[0].ime").value("Ana"));
        verify(service).komanda(5L, new KomandaCmd(TipKomande.SLEDECI, null));
    }

    @Test
    void idiNaSaVrednoscu() throws Exception {
        when(service.komanda(eq(5L), any())).thenReturn(STANJE);
        mvc.perform(post("/izvodjenja/5/komande").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tip\":\"IDI_NA\",\"vrednost\":-1}"))
           .andExpect(status().isOk());
        verify(service).komanda(5L, new KomandaCmd(TipKomande.IDI_NA, -1));
    }

    @Test
    void nepoznatTipJe400() throws Exception {
        mvc.perform(post("/izvodjenja/5/komande").contentType(MediaType.APPLICATION_JSON).content("{\"tip\":\"XYZ\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        verifyNoInteractions(service);
    }

    @Test
    void greskaServisaZadrzavaStatusIPoruku() throws Exception {
        when(service.komanda(eq(5L), any())).thenThrow(new SystemException("Prvo zatvori pitanje.", HttpStatus.CONFLICT));
        mvc.perform(post("/izvodjenja/5/komande").contentType(MediaType.APPLICATION_JSON).content("{\"tip\":\"TACAN\"}"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Prvo zatvori pitanje."));
        when(service.komanda(eq(6L), any())).thenThrow(new SystemException("Izvođenje je završeno.", HttpStatus.GONE));
        mvc.perform(post("/izvodjenja/6/komande").contentType(MediaType.APPLICATION_JSON).content("{\"tip\":\"SLEDECI\"}"))
           .andExpect(status().isGone())
           .andExpect(jsonPath("$.reason").value("Izvođenje je završeno."));
    }

    @Test
    void stanjeILista() throws Exception {
        when(stanjeService.nastavnicko(5L)).thenReturn(STANJE);
        mvc.perform(get("/izvodjenja/5/stanje"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.prikaz").value("SLAJD"));
        when(service.lista(1L, StatusIzvodjenja.AKTIVNO)).thenReturn(List.of(INFO));
        mvc.perform(get("/izvodjenja").param("prezentacijaId", "1").param("status", "AKTIVNO"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(5))
           .andExpect(jsonPath("$[0].pocetak").value("2026-10-07T12:00:00"))
           .andExpect(jsonPath("$[0].brojUcesnika").value(2));
        when(service.lista(null, null)).thenReturn(List.of());
        mvc.perform(get("/izvodjenja")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/izvodjenja").param("status", "NESTO"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: status."));
    }

    @Test
    void moderacija() throws Exception {
        when(service.preimenuj(5L, 31L, "Ana B")).thenReturn(STANJE);
        mvc.perform(put("/izvodjenja/5/ucesnici/31").contentType(MediaType.APPLICATION_JSON).content("{\"ime\":\"Ana B\"}"))
           .andExpect(status().isOk());
        when(service.izbaci(5L, 31L)).thenReturn(STANJE);
        mvc.perform(delete("/izvodjenja/5/ucesnici/31"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.verzija").value(4));
        when(service.sakrij(eq(5L), eq(101L), any())).thenReturn(STANJE);
        mvc.perform(put("/izvodjenja/5/runde/101/sakrij").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kljuc\":\"celik\",\"sakriven\":true}"))
           .andExpect(status().isOk());
        verify(service).sakrij(5L, 101L, new SakrijCmd("celik", true));
    }

    @Test
    void rezultatiIBrisanje() throws Exception {
        when(service.rezultati(5L)).thenReturn(new IzvodjenjeRezultati(INFO, List.of(), List.of()));
        mvc.perform(get("/izvodjenja/5/rezultati"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.izvodjenje.id").value(5))
           .andExpect(jsonPath("$.pitanja").isArray());
        mvc.perform(delete("/izvodjenja/5")).andExpect(status().isNoContent());
        verify(service).obrisi(5L);
        doThrow(new SystemException("Izvođenje je u toku.", HttpStatus.CONFLICT)).when(service).obrisi(6L);
        mvc.perform(delete("/izvodjenja/6"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Izvođenje je u toku."));
    }
}
