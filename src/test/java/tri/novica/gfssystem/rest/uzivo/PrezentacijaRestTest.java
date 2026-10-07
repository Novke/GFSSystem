package tri.novica.gfssystem.rest.uzivo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PrezentacijaRest.class, SlajdRest.class})
class PrezentacijaRestTest {

    static final LocalDateTime IZMENJENO = LocalDateTime.of(2026, 10, 7, 12, 0);
    static final PrezentacijaDetails DETAILS = new PrezentacijaDetails(1L, "Uvod", null, new PredmetKratko(7L, "Statika"),
            0, 0, IZMENJENO, false, TelefonPrikaz.DUGMAD, true, 0, null, List.of());
    static final SlajdDetails SLAJD = new SlajdDetails(9L, 2, TipSlajda.INFO, "Uvod", null, null, null, false, null);

    @Autowired MockMvc mvc;
    @MockitoBean PrezentacijaService service;

    @Test
    void kreirajVraca201IDetalje() throws Exception {
        when(service.kreiraj(any())).thenReturn(DETAILS);
        mvc.perform(post("/prezentacije").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predmetId\":7,\"naziv\":\"Uvod\"}"))
           .andExpect(status().isCreated())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.id").value(1))
           .andExpect(jsonPath("$.predmet.naziv").value("Statika"))
           .andExpect(jsonPath("$.izmenjeno").value("2026-10-07T12:00:00"))
           .andExpect(jsonPath("$.telefonPrikaz").value("DUGMAD"))
           .andExpect(jsonPath("$.aktivnoIzvodjenjeId").value(org.hamcrest.Matchers.nullValue()))
           .andExpect(jsonPath("$.slajdovi").isArray());
        verify(service).kreiraj(new CreatePrezentacijaCmd(7L, "Uvod", null));
    }

    @Test
    void redosledProsledjujeListu() throws Exception {
        when(service.redosled(eq(1L), any())).thenReturn(DETAILS);
        mvc.perform(put("/prezentacije/1/redosled").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slajdIds\":[3,1,2]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(1));
        verify(service).redosled(1L, List.of(3L, 1L, 2L));
    }

    @Test
    void losJsonJe400() throws Exception {
        mvc.perform(post("/prezentacije").contentType(MediaType.APPLICATION_JSON).content("{\"naziv\":"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        mvc.perform(put("/slajdovi/9").contentType(MediaType.APPLICATION_JSON).content("{\"tip\":\"XYZ\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        mvc.perform(put("/prezentacije/1/redosled").contentType(MediaType.APPLICATION_JSON).content(""))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        verifyNoInteractions(service);
    }

    @Test
    void losParametarJe400() throws Exception {
        mvc.perform(get("/prezentacije").param("predmetId", "abc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: predmetId."));
        mvc.perform(post("/prezentacije/1/slajdovi").param("posle", "x").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tip\":\"INFO\",\"naslov\":\"A\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: posle."));
        verifyNoInteractions(service);
    }

    @Test
    void listaSaIBezPredmeta() throws Exception {
        when(service.lista(any())).thenReturn(List.of());
        mvc.perform(get("/prezentacije").param("predmetId", "7")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/prezentacije")).andExpect(status().isOk());
        verify(service).lista(7L);
        verify(service).lista(null);
    }

    @Test
    void detaljiIzmenaBrisanjeIDupliranjePrezentacije() throws Exception {
        when(service.detalji(1L)).thenReturn(DETAILS);
        when(service.izmeni(eq(1L), any())).thenReturn(DETAILS);
        when(service.dupliraj(1L)).thenReturn(DETAILS);

        mvc.perform(get("/prezentacije/1")).andExpect(status().isOk()).andExpect(jsonPath("$.naziv").value("Uvod"));
        mvc.perform(put("/prezentacije/1").contentType(MediaType.APPLICATION_JSON).content("""
                {"naziv":"Novi","opis":null,"takmicenje":true,"telefonPrikaz":"PITANJE","detaljiDozvoljeni":false}"""))
           .andExpect(status().isOk());
        verify(service).izmeni(1L, new UpdatePrezentacijaCmd("Novi", null, true, TelefonPrikaz.PITANJE, false));
        mvc.perform(delete("/prezentacije/1")).andExpect(status().isNoContent());
        verify(service).obrisi(1L);
        mvc.perform(post("/prezentacije/1/dupliraj")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void brisanjeSaAktivnimIzvodjenjemJe409() throws Exception {
        doThrow(new SystemException("Prezentacija ima izvođenje u toku. Završi ga pre brisanja.", HttpStatus.CONFLICT))
                .when(service).obrisi(1L);
        mvc.perform(delete("/prezentacije/1"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Prezentacija ima izvođenje u toku. Završi ga pre brisanja."));
    }

    @Test
    void dodajSlajdSaPitanjemIPosle() throws Exception {
        when(service.dodajSlajd(eq(1L), any(), any())).thenReturn(SLAJD);
        mvc.perform(post("/prezentacije/1/slajdovi").param("posle", "5").contentType(MediaType.APPLICATION_JSON).content("""
                {"tip":"PITANJE","naslov":null,"sadrzaj":null,"slikaId":null,"beleske":"b","postepeno":false,
                 "pitanje":{"tip":"BROJ","tekst":"g?","slikaId":null,"vremeSekunde":30,"opcije":[],
                  "brojTacno":9.81,"brojOdstupanje":0.1,"odstupanjeTip":"APSOLUTNO","jedinica":"m/s²",
                  "tekstPrikaz":null,"prihvatljiviOdgovori":[],"skalaMinOznaka":null,"skalaMaxOznaka":null}}"""))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(9))
           .andExpect(jsonPath("$.rb").value(2));

        ArgumentCaptor<SlajdCmd> cmd = ArgumentCaptor.forClass(SlajdCmd.class);
        verify(service).dodajSlajd(eq(1L), cmd.capture(), eq(5L));
        assertEquals(TipSlajda.PITANJE, cmd.getValue().tip());
        assertEquals("b", cmd.getValue().beleske());
        PitanjeCmd p = cmd.getValue().pitanje();
        assertEquals(TipPitanja.BROJ, p.tip());
        assertEquals(30, p.vremeSekunde());
        assertEquals(9.81, p.brojTacno());
        assertEquals(OdstupanjeTip.APSOLUTNO, p.odstupanjeTip());
        assertEquals("m/s²", p.jedinica());

        mvc.perform(post("/prezentacije/1/slajdovi").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tip\":\"INFO\",\"naslov\":\"A\"}"))
           .andExpect(status().isCreated());
        verify(service).dodajSlajd(1L, new SlajdCmd(TipSlajda.INFO, "A", null, null, null, false, null), null);
    }

    @Test
    void slajdIzmenaBrisanjeIDupliranje() throws Exception {
        when(service.izmeniSlajd(eq(9L), any())).thenReturn(SLAJD);
        when(service.duplirajSlajd(9L)).thenReturn(SLAJD);

        mvc.perform(put("/slajdovi/9").contentType(MediaType.APPLICATION_JSON).content("""
                {"tip":"PITANJE","pitanje":{"tip":"JEDAN_TACAN","tekst":"?","opcije":[{"tekst":"a","tacna":true},{"tekst":"b","tacna":false}]}}"""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.naslov").value("Uvod"));
        ArgumentCaptor<SlajdCmd> cmd = ArgumentCaptor.forClass(SlajdCmd.class);
        verify(service).izmeniSlajd(eq(9L), cmd.capture());
        assertEquals(List.of(new OpcijaCmd("a", true), new OpcijaCmd("b", false)), cmd.getValue().pitanje().opcije());

        mvc.perform(delete("/slajdovi/9")).andExpect(status().isNoContent());
        verify(service).obrisiSlajd(9L);
        mvc.perform(post("/slajdovi/9/dupliraj")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void nepostojeciSlajdJe404() throws Exception {
        doThrow(new SystemException("Slajd nije pronađen.", HttpStatus.NOT_FOUND)).when(service).obrisiSlajd(99L);
        mvc.perform(delete("/slajdovi/99"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Slajd nije pronađen."));
    }

    @Test
    void predavanjaZaPokretanje() throws Exception {
        when(service.predavanjaZaPokretanje(1L)).thenReturn(List.of(new PredavanjeZaPokretanjeInfo(40L, 3,
                LocalDate.of(2026, 10, 7), "Reakcije", false, new GrupaKratko(5L, "GD-2025"))));
        mvc.perform(get("/prezentacije/1/predavanja"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(40))
           .andExpect(jsonPath("$[0].rb").value(3))
           .andExpect(jsonPath("$[0].datum").value("2026-10-07"))
           .andExpect(jsonPath("$[0].zavrseno").value(false))
           .andExpect(jsonPath("$[0].grupa.naziv").value("GD-2025"));
    }
}
