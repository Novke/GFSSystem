package tri.novica.gfssystem.rest.banka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.RenderNedostupan;
import tri.novica.gfssystem.service.banka.VarijantaService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VarijantaRest.class)
class VarijantaRestTest {

    static final LocalDateTime T = LocalDateTime.of(2026, 10, 17, 12, 0);
    static final String PNG = "5b3e4d1c-8f90-4e3c-9d4b-2c3d4e5f6071";
    static final String SVG = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";
    static final Polje POLJE = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, 0.0, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVORI = Map.of("a", new OdgovorPolja(11.0, null, null, null));
    static final VarijantaDetails DETAILS = new VarijantaDetails(11L, 5L,
            List.of(new GrupaPutanja(3L, "Konverzije"), new GrupaPutanja(5L, "2 → 10")), 1, "V1", "Pretvori $1011_2$.", "\\foo",
            ODGOVORI, List.of(POLJE), List.of(new PrilogInfo("skica", new MedijInfo(PNG, "skica.png", "image/png", 120))),
            StatusVarijante.NACRT, IzvorVarijante.RUCNO, RenderStatus.GRESKA, SVG, null,
            new RenderGreskaInfo(1, "Undefined control sequence."), "resenje", T, T, true, 9L, "uvoz-1");
    static final VarijantaCmd CMD = new VarijantaCmd("V1", "Pretvori $1011_2$.", null, ODGOVORI,
            List.of(new PrilogCmd("skica", PNG)), IzvorVarijante.CLAUDE);
    static final String TELO = """
            {"naziv":"V1","tekst":"Pretvori $1011_2$.","odgovori":{"a":{"broj":11}},
             "prilozi":[{"ime":"skica","medijId":"%s"}],"izvor":"CLAUDE"}""".formatted(PNG);

    @Autowired MockMvc mvc;
    @MockitoBean VarijantaService service;

    @Test
    void listaVraca200IFiltriraPoStatusu() throws Exception {
        when(service.lista(5L, StatusVarijante.AKTIVNA)).thenReturn(List.of(new VarijantaInfo(11L, 5L, 1, "V1",
                StatusVarijante.AKTIVNA, IzvorVarijante.RUCNO, RenderStatus.OK, SVG, null, T, true, null, null)));
        mvc.perform(get("/banka/grupe/5/varijante").param("status", "AKTIVNA"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$[0].id").value(11))
           .andExpect(jsonPath("$[0].grupaId").value(5))
           .andExpect(jsonPath("$[0].svgId").value(SVG))
           .andExpect(jsonPath("$[0].resenjeSvgId").value(nullValue()))
           .andExpect(jsonPath("$[0].renderStatus").value("OK"))
           .andExpect(jsonPath("$[0].izmenjeno").value("2026-10-17T12:00:00"))
           .andExpect(jsonPath("$[0].ikadOdobrena").value(true))
           .andExpect(jsonPath("$[0].zamenjuje").value(nullValue()))
           .andExpect(jsonPath("$[0].serija").value(nullValue()));

        when(service.lista(5L, null)).thenReturn(List.of());
        mvc.perform(get("/banka/grupe/5/varijante")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void nepoznatStatusJe400() throws Exception {
        mvc.perform(get("/banka/grupe/5/varijante").param("status", "XYZ"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: status."));
        verifyNoInteractions(service);
    }

    @Test
    void kreirajVraca201IDetaljeSaGreskomRendera() throws Exception {
        when(service.kreiraj(eq(5L), any())).thenReturn(DETAILS);
        mvc.perform(post("/banka/grupe/5/varijante").contentType(MediaType.APPLICATION_JSON).content(TELO))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(11))
           .andExpect(jsonPath("$.putanja[1].naziv").value("2 → 10"))
           .andExpect(jsonPath("$.odgovori.a.broj").value(11.0))
           .andExpect(jsonPath("$.polja[0].kljuc").value("a"))
           .andExpect(jsonPath("$.prilozi[0].ime").value("skica"))
           .andExpect(jsonPath("$.prilozi[0].medij.mime").value("image/png"))
           .andExpect(jsonPath("$.renderStatus").value("GRESKA"))
           .andExpect(jsonPath("$.renderGreska.linija").value(1))
           .andExpect(jsonPath("$.renderGreska.poruka").value("Undefined control sequence."))
           .andExpect(jsonPath("$.renderDeo").value("resenje"))
           .andExpect(jsonPath("$.kreirano").value("2026-10-17T12:00:00"))
           .andExpect(jsonPath("$.ikadOdobrena").value(true))
           .andExpect(jsonPath("$.zamenjuje").value(9))
           .andExpect(jsonPath("$.serija").value("uvoz-1"));
        verify(service).kreiraj(5L, CMD);
    }

    @Test
    void odgovorSaNepoznatimSvojstvomIliNullVrednoscuJe400() throws Exception {
        for (String odgovori : List.of("{\"a\":{\"broj\":11,\"visak\":1}}", "{\"a\":null}", "{\"a\":{\"broj\":\"11\"}}",
                "{\"a\":{\"tacne\":[1,null]}}", "[]")) {
            mvc.perform(post("/banka/grupe/5/varijante").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tekst\":\"x\",\"odgovori\":" + odgovori + "}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        }
        verifyNoInteractions(service);
    }

    @Test
    void odgovoriNullIIzostavljeniStizuKaoNull() throws Exception {
        when(service.kreiraj(eq(5L), any())).thenReturn(DETAILS);
        mvc.perform(post("/banka/grupe/5/varijante").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tekst\":\"x\",\"odgovori\":null}"))
           .andExpect(status().isCreated());
        verify(service).kreiraj(5L, new VarijantaCmd(null, "x", null, null, null, null));
    }

    @Test
    void detaljiIzmenaIBrisanje() throws Exception {
        when(service.detalji(11L)).thenReturn(DETAILS);
        mvc.perform(get("/banka/varijante/11")).andExpect(status().isOk()).andExpect(jsonPath("$.tekst").value("Pretvori $1011_2$."));

        when(service.izmeni(eq(11L), any())).thenReturn(DETAILS);
        mvc.perform(put("/banka/varijante/11").contentType(MediaType.APPLICATION_JSON).content(TELO))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(11));
        verify(service).izmeni(11L, CMD);

        mvc.perform(delete("/banka/varijante/11")).andExpect(status().isNoContent());
        verify(service).obrisi(11L);
    }

    @Test
    void akcijeNadVarijantomVracaju200ADupliranje201() throws Exception {
        when(service.odobri(11L)).thenReturn(DETAILS);
        when(service.arhiviraj(11L)).thenReturn(DETAILS);
        when(service.vratiUNacrt(11L)).thenReturn(DETAILS);
        when(service.render(11L)).thenReturn(DETAILS);
        when(service.dupliraj(11L)).thenReturn(DETAILS);

        for (String akcija : List.of("odobri", "arhiviraj", "vrati-u-nacrt", "render")) {
            mvc.perform(post("/banka/varijante/11/" + akcija)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(11));
        }
        mvc.perform(post("/banka/varijante/11/dupliraj")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(11));
        verify(service).odobri(11L);
        verify(service).arhiviraj(11L);
        verify(service).vratiUNacrt(11L);
        verify(service).render(11L);
        verify(service).dupliraj(11L);
    }

    @Test
    void renderProbaVracaSvgKaoTekst() throws Exception {
        when(service.proba(any())).thenReturn(new RenderProbaInfo(true, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>", null, null, null));
        mvc.perform(post("/banka/render-proba").contentType(MediaType.APPLICATION_JSON).content("""
                        {"predmetId":7,"tekst":"\\\\includegraphics{skica}","prilozi":[{"ime":"skica","medijId":"%s"}]}""".formatted(PNG)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.ok").value(true))
           .andExpect(jsonPath("$.svg").value("<svg xmlns=\"http://www.w3.org/2000/svg\"/>"))
           .andExpect(jsonPath("$.greska").value(nullValue()));
        verify(service).proba(new RenderProbaCmd(7L, "\\includegraphics{skica}", null, List.of(new PrilogCmd("skica", PNG))));
    }

    @Test
    void nedostupanRenderJe503() throws Exception {
        when(service.kreiraj(eq(5L), any())).thenThrow(new RenderNedostupan());
        when(service.izmeni(eq(11L), any())).thenThrow(new RenderNedostupan(new RuntimeException("Connection refused")));
        when(service.render(11L)).thenThrow(new RenderNedostupan());

        mvc.perform(post("/banka/grupe/5/varijante").contentType(MediaType.APPLICATION_JSON).content(TELO))
           .andExpect(status().isServiceUnavailable())
           .andExpect(jsonPath("$.reason").value("Render servis nije dostupan."));
        mvc.perform(put("/banka/varijante/11").contentType(MediaType.APPLICATION_JSON).content(TELO))
           .andExpect(status().isServiceUnavailable())
           .andExpect(jsonPath("$.reason").value("Render servis nije dostupan."));
        mvc.perform(post("/banka/varijante/11/render")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void greskeServisaZadrzavajuStatusIPoruku() throws Exception {
        when(service.proba(any())).thenThrow(new SystemException("Previše proba u isto vreme.", HttpStatus.TOO_MANY_REQUESTS));
        when(service.odobri(11L)).thenThrow(new SystemException("Varijanta se odobrava tek kad je render uspeo.", HttpStatus.CONFLICT));
        when(service.detalji(12L)).thenThrow(new SystemException("Varijanta nije pronađena.", HttpStatus.NOT_FOUND));

        mvc.perform(post("/banka/render-proba").contentType(MediaType.APPLICATION_JSON).content("{\"predmetId\":7,\"tekst\":\"x\"}"))
           .andExpect(status().isTooManyRequests())
           .andExpect(jsonPath("$.reason").value("Previše proba u isto vreme."));
        mvc.perform(post("/banka/varijante/11/odobri"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Varijanta se odobrava tek kad je render uspeo."));
        mvc.perform(get("/banka/varijante/12")).andExpect(status().isNotFound());
        mvc.perform(get("/banka/varijante/abc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: id."));
    }
}
