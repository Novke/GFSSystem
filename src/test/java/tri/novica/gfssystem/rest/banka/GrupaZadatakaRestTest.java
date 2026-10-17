package tri.novica.gfssystem.rest.banka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.validation.BankaPP;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GrupaZadatakaRest.class)
class GrupaZadatakaRestTest {

    static final LocalDateTime IZMENJENO = LocalDateTime.of(2026, 10, 17, 12, 0);
    static final Polje POLJE = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, 0.0, null, null, null, null, null);
    static final GrupaDetails DETAILS = new GrupaDetails(5L, 7L, 3L, Namena.PREDAVANJE, "2 → 10", null, 1, 2, null,
            List.of(POLJE), List.of(new GrupaPutanja(3L, "Konverzije"), new GrupaPutanja(5L, "2 → 10")), true, 3, 1, 2, 1, IZMENJENO);

    @Autowired MockMvc mvc;
    @MockitoBean GrupaZadatakaService service;

    @Test
    void stabloVraca200ICvoroveSaDecom() throws Exception {
        GrupaCvor list = new GrupaCvor(5L, "2 → 10", 1, 2, true, 3, 1, 2, 1, List.of());
        when(service.stablo(7L, Namena.PREDAVANJE))
                .thenReturn(List.of(new GrupaCvor(3L, "Konverzije", 1, null, false, 3, 1, 2, 1, List.of(list))));
        mvc.perform(get("/banka/stablo").param("predmetId", "7").param("namena", "PREDAVANJE"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$[0].id").value(3))
           .andExpect(jsonPath("$[0].list").value(false))
           .andExpect(jsonPath("$[0].tezina").value(org.hamcrest.Matchers.nullValue()))
           .andExpect(jsonPath("$[0].brojVarijanti").value(3))
           .andExpect(jsonPath("$[0].deca[0].naziv").value("2 → 10"))
           .andExpect(jsonPath("$[0].deca[0].brojGresaka").value(1))
           .andExpect(jsonPath("$[0].deca[0].deca").isEmpty());
    }

    @Test
    void stabloSaNepoznatomNamenomIliBezPredmetaJe400() throws Exception {
        mvc.perform(get("/banka/stablo").param("predmetId", "7").param("namena", "XYZ"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: namena."));
        mvc.perform(get("/banka/stablo").param("namena", "TEST"))
           .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void kreirajVraca201IDetalje() throws Exception {
        when(service.kreiraj(any())).thenReturn(DETAILS);
        mvc.perform(post("/banka/grupe").contentType(MediaType.APPLICATION_JSON).content("""
                        {"predmetId":7,"roditeljId":3,"naziv":"2 → 10","tezina":2,
                         "polja":[{"kljuc":"a","oznaka":"a) binarni u dekadni","tip":"BROJ","odstupanje":0}]}"""))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(5))
           .andExpect(jsonPath("$.namena").value("PREDAVANJE"))
           .andExpect(jsonPath("$.putanja[0].naziv").value("Konverzije"))
           .andExpect(jsonPath("$.polja[0].kljuc").value("a"))
           .andExpect(jsonPath("$.list").value(true))
           .andExpect(jsonPath("$.izmenjeno").value("2026-10-17T12:00:00"));
        verify(service).kreiraj(new GrupaCmd(7L, 3L, null, "2 → 10", null, 2, null, List.of(POLJE)));
    }

    @Test
    void porukaIzBankaPPJe400() throws Exception {
        when(service.kreiraj(any())).thenAnswer(inv -> {
            GrupaCmd c = inv.getArgument(0);
            BankaPP.proveriGrupu(c.naziv(), c.tezina(), c.uputstvo());
            return DETAILS;
        });
        mvc.perform(post("/banka/grupe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predmetId\":7,\"namena\":\"TEST\",\"naziv\":\"Kolokvijum\",\"tezina\":6}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Težina mora biti od 1 do 5."));
    }

    @Test
    void poljaSeCitajuStrogo() throws Exception {
        // nepoznato svojstvo polja, broj umesto teksta, null element i decimalna težina se ne gube tiho
        for (String telo : List.of(
                "{\"naziv\":\"G\",\"polja\":[{\"kljuc\":\"a\",\"oznaka\":\"a\",\"tip\":\"BROJ\",\"tolerancija\":0.1}]}",
                "{\"naziv\":\"G\",\"polja\":[{\"kljuc\":1,\"oznaka\":\"a\",\"tip\":\"BROJ\"}]}",
                "{\"naziv\":\"G\",\"polja\":[null]}",
                "{\"naziv\":\"G\",\"polja\":{\"kljuc\":\"a\"}}",
                "{\"naziv\":\"G\",\"tezina\":2.5}")) {
            mvc.perform(post("/banka/grupe").contentType(MediaType.APPLICATION_JSON).content(telo))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        }
        verifyNoInteractions(service);

        when(service.kreiraj(any())).thenReturn(DETAILS);
        mvc.perform(post("/banka/grupe").contentType(MediaType.APPLICATION_JSON).content("{\"naziv\":\"G\",\"polja\":null}"))
           .andExpect(status().isCreated());
        verify(service).kreiraj(new GrupaCmd(null, null, null, "G", null, null, null, null));
    }

    @Test
    void detaljiIzmenaIBrisanje() throws Exception {
        when(service.detalji(5L)).thenReturn(DETAILS);
        mvc.perform(get("/banka/grupe/5"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.brojAktivnih").value(1))
           .andExpect(jsonPath("$.roditeljId").value(3));

        when(service.izmeni(eq(5L), any())).thenReturn(DETAILS);
        mvc.perform(put("/banka/grupe/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naziv\":\"2 → 10\",\"opis\":\"Brojni sistemi\",\"tezina\":3,\"uputstvo\":\"Do 255.\",\"polja\":[]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(5));
        verify(service).izmeni(5L, new GrupaCmd(null, null, null, "2 → 10", "Brojni sistemi", 3, "Do 255.", List.of()));

        mvc.perform(delete("/banka/grupe/5"))
           .andExpect(status().isNoContent())
           .andExpect(content().string(""));
        verify(service).obrisi(5L);
    }

    @Test
    void konfliktiServisaProlazeKao409() throws Exception {
        doThrow(new SystemException("Grupa ima podgrupe.", HttpStatus.CONFLICT)).when(service).obrisi(3L);
        mvc.perform(delete("/banka/grupe/3"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Grupa ima podgrupe."));

        when(service.izmeni(eq(5L), any()))
                .thenThrow(new SystemException("Tip polja 'a' se ne menja dok grupa ima aktivne varijante.", HttpStatus.CONFLICT));
        mvc.perform(put("/banka/grupe/5").contentType(MediaType.APPLICATION_JSON).content("{\"naziv\":\"G\"}"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Tip polja 'a' se ne menja dok grupa ima aktivne varijante."));

        when(service.detalji(9L)).thenThrow(new SystemException("Grupa nije pronađena.", HttpStatus.NOT_FOUND));
        mvc.perform(get("/banka/grupe/9"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Grupa nije pronađena."));
    }

    @Test
    void premestiVraca200() throws Exception {
        when(service.premesti(eq(5L), any())).thenReturn(DETAILS);
        mvc.perform(post("/banka/grupe/5/premesti").contentType(MediaType.APPLICATION_JSON).content("{\"roditeljId\":3,\"rb\":1}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.rb").value(1));
        verify(service).premesti(5L, new PremestiCmd(3L, 1));

        mvc.perform(post("/banka/grupe/5/premesti").contentType(MediaType.APPLICATION_JSON).content("{\"roditeljId\":null}"))
           .andExpect(status().isOk());
        verify(service).premesti(5L, new PremestiCmd(null, null));
    }

    @Test
    void redosledVraca204ZaKoreneIPodgrupe() throws Exception {
        mvc.perform(put("/banka/grupe/redosled").param("predmetId", "7").param("namena", "PREDAVANJE")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[4,3]}"))
           .andExpect(status().isNoContent());
        verify(service).redosled(null, 7L, Namena.PREDAVANJE, new RedosledGrupaCmd(List.of(4L, 3L)));

        mvc.perform(put("/banka/grupe/redosled").param("predmetId", "7").param("namena", "TEST").param("roditeljId", "3")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[6,5]}"))
           .andExpect(status().isNoContent());
        verify(service).redosled(3L, 7L, Namena.TEST, new RedosledGrupaCmd(List.of(6L, 5L)));

        mvc.perform(put("/banka/grupe/redosled").param("predmetId", "7").param("namena", "xyz")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: namena."));
    }
}
