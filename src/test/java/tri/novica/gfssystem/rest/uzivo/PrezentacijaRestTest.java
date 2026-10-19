package tri.novica.gfssystem.rest.uzivo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.dto.uzivo.platno.Platno;
import tri.novica.gfssystem.service.uzivo.PlatnoJson;
import tri.novica.gfssystem.service.uzivo.PlatnoTestPodaci;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.SmerPrelaza;
import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;
import tri.novica.gfssystem.entity.uzivo.Tema;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipPrelaza;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
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
            0, 0, IZMENJENO, false, TelefonPrikaz.DUGMAD, true, 0, null, Tema.NACRT, List.of());
    static final SlajdDetails SLAJD = new SlajdDetails(9L, 2, TipSlajda.INFO, "Uvod", null, null, null, false, null, null, null);

    @Autowired MockMvc mvc;
    @MockitoBean PrezentacijaService service;
    @MockitoBean IzvodjenjeService izvodjenjeService;

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
        verify(service).kreiraj(new CreatePrezentacijaCmd(7L, "Uvod", null, null));
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
        verify(service).izmeni(1L, new UpdatePrezentacijaCmd("Novi", null, true, TelefonPrikaz.PITANJE, false, null));
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
        verify(service).dodajSlajd(1L, new SlajdCmd(TipSlajda.INFO, "A", null, null, null, false, null, null, null), null);
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
    void prelazSlajdaKrozPut() throws Exception {
        SlajdDetails saPrelazom = new SlajdDetails(7L, 1, TipSlajda.INFO, "Uvod", null, null, null, false, null, null,
                new Prelaz(TipPrelaza.GURANJE, SmerPrelaza.DOLE, 600));
        when(service.izmeniSlajd(eq(7L), any())).thenReturn(saPrelazom);

        mvc.perform(put("/slajdovi/7").contentType(MediaType.APPLICATION_JSON).content("""
                {"tip":"INFO","naslov":"Uvod","prelaz":{"efekat":"GURANJE","smer":"DOLE","trajanjeMs":600}}"""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.prelaz.efekat").value("GURANJE"))
           .andExpect(jsonPath("$.prelaz.smer").value("DOLE"))
           .andExpect(jsonPath("$.prelaz.trajanjeMs").value(600));
        ArgumentCaptor<SlajdCmd> cmd = ArgumentCaptor.forClass(SlajdCmd.class);
        verify(service).izmeniSlajd(eq(7L), cmd.capture());
        assertEquals(new Prelaz(TipPrelaza.GURANJE, SmerPrelaza.DOLE, 600), cmd.getValue().prelaz());
    }

    @Test
    void zadatakSlajdKrozPutIOdgovorNosiOpis() throws Exception {
        ZadatakSlajdaInfo opis = new ZadatakSlajdaInfo(5L, null, 4, "2 → 10", List.of(new GrupaPutanja(1L, "Konverzije"),
                new GrupaPutanja(5L, "2 → 10")), true, 3, List.of(), false, List.of(), List.of());
        when(service.izmeniSlajd(eq(1L), any())).thenReturn(new SlajdDetails(1L, 1, TipSlajda.ZADATAK, null, null, null, null,
                false, null, null, null, opis));

        mvc.perform(put("/slajdovi/1").contentType(MediaType.APPLICATION_JSON).content("""
                {"tip":"ZADATAK","zadatak":{"grupaId":5,"vremeSekunde":null,"brojVarijanti":4}}"""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.tip").value("ZADATAK"))
           .andExpect(jsonPath("$.zadatak.grupaId").value(5))
           .andExpect(jsonPath("$.zadatak.vremeSekunde").value(org.hamcrest.Matchers.nullValue()))
           .andExpect(jsonPath("$.zadatak.brojVarijanti").value(4))
           .andExpect(jsonPath("$.zadatak.grupaNaziv").value("2 → 10"))
           .andExpect(jsonPath("$.zadatak.putanja[1].naziv").value("2 → 10"))
           .andExpect(jsonPath("$.zadatak.brojAktivnihVarijanti").value(3))
           .andExpect(jsonPath("$.zadatak.list").value(true))
           .andExpect(jsonPath("$.zadatak.upozorenja").isEmpty());
        ArgumentCaptor<SlajdCmd> cmd = ArgumentCaptor.forClass(SlajdCmd.class);
        verify(service).izmeniSlajd(eq(1L), cmd.capture());
        assertEquals(TipSlajda.ZADATAK, cmd.getValue().tip());
        assertEquals(5L, cmd.getValue().zadatak().grupaId());
        assertNull(cmd.getValue().zadatak().vremeSekunde());
        assertEquals(4, cmd.getValue().zadatak().brojVarijanti());
    }

    @Test
    void slajdBezZadatkaImaZadatakNullUOdgovoruIUTelu() throws Exception {
        when(service.izmeniSlajd(eq(9L), any())).thenReturn(SLAJD);

        mvc.perform(put("/slajdovi/9").contentType(MediaType.APPLICATION_JSON).content("""
                {"tip":"INFO","naslov":"Uvod"}"""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.zadatak").value(org.hamcrest.Matchers.nullValue()));
        ArgumentCaptor<SlajdCmd> cmd = ArgumentCaptor.forClass(SlajdCmd.class);
        verify(service).izmeniSlajd(eq(9L), cmd.capture());
        assertNull(cmd.getValue().zadatak());
    }

    @Test
    void teloBezPrelazaJeBezPrelazaIOdgovorImaPrelazNull() throws Exception {
        when(service.izmeniSlajd(eq(7L), any())).thenReturn(SLAJD);
        mvc.perform(put("/slajdovi/7").contentType(MediaType.APPLICATION_JSON).content("{\"tip\":\"INFO\",\"naslov\":\"Uvod\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", org.hamcrest.Matchers.hasKey("prelaz")))
           .andExpect(jsonPath("$.prelaz").value(org.hamcrest.Matchers.nullValue()));
        ArgumentCaptor<SlajdCmd> cmd = ArgumentCaptor.forClass(SlajdCmd.class);
        verify(service).izmeniSlajd(eq(7L), cmd.capture());
        assertNull(cmd.getValue().prelaz());
    }

    @Test
    void nepoznatEfekatPrelazaJe400() throws Exception {
        mvc.perform(put("/slajdovi/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tip\":\"INFO\",\"prelaz\":{\"efekat\":\"KOCKA\"}}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        verify(service, never()).izmeniSlajd(any(), any());
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

    @Test
    void pokreniVraca201IInfo() throws Exception {
        when(izvodjenjeService.pokreni(eq(1L), any())).thenReturn(new IzvodjenjeInfo(9L,
                new PrezentacijaKratko(1L, "Uvod", 7L), "042424", StatusIzvodjenja.AKTIVNO, true,
                new GrupaKratko(3L, "GD-2025"), null, IZMENJENO, null, 0, 0));
        mvc.perform(post("/prezentacije/1/izvodjenja").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuvanje\":true,\"grupaId\":3,\"predavanjeId\":null}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(9))
           .andExpect(jsonPath("$.kod").value("042424"))
           .andExpect(jsonPath("$.prezentacija.predmetId").value(7))
           .andExpect(jsonPath("$.grupa.naziv").value("GD-2025"))
           .andExpect(jsonPath("$.predavanje").value(org.hamcrest.Matchers.nullValue()));
        verify(izvodjenjeService).pokreni(1L, new PokreniCmd(true, 3L, null));
    }

    @Test
    void pokreniBodovanRun() throws Exception {
        when(izvodjenjeService.pokreni(eq(1L), any())).thenReturn(new IzvodjenjeInfo(9L,
                new PrezentacijaKratko(1L, "Uvod", 7L), "042424", StatusIzvodjenja.AKTIVNO, true,
                new GrupaKratko(3L, "GD-2025"), null, IZMENJENO, null, 0, 0, true));
        // Jackson puni kanonski konstruktor record-a i kad postoji kraći
        mvc.perform(post("/prezentacije/1/izvodjenja").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuvanje\":false,\"grupaId\":null,\"predavanjeId\":40,\"boduje\":true}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.boduje").value(true));
        verify(izvodjenjeService).pokreni(1L, new PokreniCmd(false, null, 40L, true));
    }

    @Test
    void platnoSlajdProlaziKaoSirovJsonINazadKaoDokument() throws Exception {
        Platno platno = PlatnoTestPodaci.ispravno();
        SlajdDetails platnoSlajd = new SlajdDetails(9L, 1, TipSlajda.PLATNO, null, null, null, null, false, null, platno, null);
        when(service.dodajSlajd(eq(1L), any(), isNull())).thenReturn(platnoSlajd);
        String telo = "{\"tip\":\"PLATNO\",\"platno\":" + new PlatnoJson().zapisi(platno) + "}";

        mvc.perform(post("/prezentacije/1/slajdovi").contentType(MediaType.APPLICATION_JSON).content(telo))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.tip").value("PLATNO"))
           .andExpect(jsonPath("$.platno.verzija").value(1))
           .andExpect(jsonPath("$.platno.slojevi[0].tip").value("TEKST"))
           .andExpect(jsonPath("$.platno.slojevi[0].pasusi[0].delovi[0].tekst").value("Prvi pasus"))
           .andExpect(jsonPath("$.platno.animacije.length()").value(4));
        ArgumentCaptor<SlajdCmd> cmd = ArgumentCaptor.forClass(SlajdCmd.class);
        verify(service).dodajSlajd(eq(1L), cmd.capture(), isNull());
        assertEquals(TipSlajda.PLATNO, cmd.getValue().tip());
        assertEquals(platno, new PlatnoJson().procitaj(cmd.getValue().platno()));
    }

    @Test
    void platnoSaNepoznatimPoljemJe400SaPorukom() throws Exception {
        // servis je lažan, ali čita dokument pravim PlatnoJson-om kao pravi servis: nepoznato polje se ne sme izgubiti tiho
        when(service.dodajSlajd(eq(1L), any(), isNull())).thenAnswer(inv -> {
            new PlatnoJson().procitaj(inv.<SlajdCmd>getArgument(1).platno());
            return SLAJD;
        });
        mvc.perform(post("/prezentacije/1/slajdovi").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tip\":\"PLATNO\",\"platno\":{\"verzija\":1,\"nepoznato\":true}}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.startsWith("Neispravan dokument platna: ")));
    }

    // ---------------------------------------------------------------- uvoz slajdova

    @Test
    void uvozVraca201() throws Exception {
        when(service.uveziSlajdove(eq(1L), any())).thenReturn(DETAILS);
        mvc.perform(post("/prezentacije/1/slajdovi/uvoz").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"posle\":5,\"slajdovi\":[{\"tip\":\"INFO\",\"naslov\":\"X\"}]}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(1));
        verify(service).uveziSlajdove(eq(1L), argThat(c -> c.posle() == 5 && c.slajdovi().size() == 1));
    }

    @Test
    void uvozOd9_9MBProlazi() throws Exception {
        when(service.uveziSlajdove(eq(1L), any())).thenReturn(DETAILS);
        String slajd = "{\"tip\":\"INFO\",\"naslov\":\"" + "a".repeat(49_000) + "\"}";
        String telo = "{\"posle\":null,\"slajdovi\":[" + String.join(",", java.util.Collections.nCopies(200, slajd)) + "]}";
        assertTrue(telo.length() > 9_800_000 && telo.length() < 10_485_760);
        mvc.perform(post("/prezentacije/1/slajdovi/uvoz").contentType(MediaType.APPLICATION_JSON).content(telo))
           .andExpect(status().isCreated());
    }

    @Test
    void uvozPrekoGraniceTelaJe413() throws Exception {
        String prefiks = "{\"posle\":null,\"slajdovi\":[],\"x\":\"";
        String sufiks = "\"}";
        String telo = prefiks + "a".repeat(10_485_761 - prefiks.length() - sufiks.length()) + sufiks;
        assertEquals(10_485_761, telo.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        mvc.perform(post("/prezentacije/1/slajdovi/uvoz").contentType(MediaType.APPLICATION_JSON).content(telo))
           .andExpect(status().isContentTooLarge())
           .andExpect(jsonPath("$.reason").value("Uvoz je prevelik (najviše 10 MB). Uvezi deo po deo (opseg slajdova)."));
        verify(service, never()).uveziSlajdove(any(), any());
    }

    @Test
    void uvozTacnoNaGraniciTelaProlazi() throws Exception {
        when(service.uveziSlajdove(eq(1L), any())).thenReturn(DETAILS);
        String prefiks = "{\"posle\":null,\"slajdovi\":[{\"tip\":\"INFO\",\"naslov\":\"X\"}],\"x\":\"";
        String sufiks = "\"}";
        String telo = prefiks + "a".repeat(10_485_760 - prefiks.length() - sufiks.length()) + sufiks;
        assertEquals(10_485_760, telo.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        mvc.perform(post("/prezentacije/1/slajdovi/uvoz").contentType(MediaType.APPLICATION_JSON).content(telo))
           .andExpect(status().isCreated());
    }

    @Test
    void uvozUNepostojecuPrezentacijuJe404() throws Exception {
        doThrow(new SystemException("Prezentacija nije pronađena.", HttpStatus.NOT_FOUND))
                .when(service).uveziSlajdove(eq(99L), any());
        mvc.perform(post("/prezentacije/99/slajdovi/uvoz").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"posle\":null,\"slajdovi\":[{\"tip\":\"INFO\",\"naslov\":\"X\"}]}"))
           .andExpect(status().isNotFound());
    }

    @Test
    void poteziSlajdaVraca200ISlojeve() throws Exception {
        when(service.poteziSlajda(7L)).thenReturn(List.of(new PoteziSloja("c1", "m1", 100.0, 50.0,
                List.of(new tri.novica.gfssystem.dto.uzivo.platno.Potez("#FF0000", 2.0, List.of(0.0, 0.0, 10.0, 5.0)))),
                new PoteziSloja("c2", "m2", 300.0, 200.0, null)));
        mvc.perform(get("/slajdovi/7/potezi"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$[0].slojId").value("c1"))
           .andExpect(jsonPath("$[0].medijId").value("m1"))
           .andExpect(jsonPath("$[0].sirina").value(100.0))
           .andExpect(jsonPath("$[0].visina").value(50.0))
           .andExpect(jsonPath("$[0].potezi[0].boja").value("#FF0000"))
           .andExpect(jsonPath("$[0].potezi[0].tacke[2]").value(10.0))
           // ugovor 3: crtež bez poteza izlazi kao "potezi": null (ključ postoji), i pod globalnim NON_NULL bi ovo palo
           .andExpect(jsonPath("$[1].slojId").value("c2"))
           .andExpect(jsonPath("$[1]").value(org.hamcrest.Matchers.hasKey("potezi")))
           .andExpect(jsonPath("$[1].potezi").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void poteziNepostojecegSlajdaJe404() throws Exception {
        when(service.poteziSlajda(99L)).thenThrow(new SystemException("Slajd nije pronađen.", HttpStatus.NOT_FOUND));
        mvc.perform(get("/slajdovi/99/potezi"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Slajd nije pronađen."));
    }
}
