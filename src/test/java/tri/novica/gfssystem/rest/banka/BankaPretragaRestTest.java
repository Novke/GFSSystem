package tri.novica.gfssystem.rest.banka;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.banka.BankaFilter;
import tri.novica.gfssystem.dto.banka.BankaPretragaStavka;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.service.banka.BankaPretragaService;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@code GET /banka/pretraga}: stranica kao {@code PagedModel}, sort sa bele liste, upit od bar 2 znaka. */
@WebMvcTest(BankaPretragaRest.class)
class BankaPretragaRestTest {

    static final String UPIT_KRATAK = "Upit mora imati bar 2 znaka.";

    @Autowired MockMvc mvc;
    @MockitoBean BankaPretragaService service;

    private void praznaStrana() {
        when(service.pretraga(any(), any())).thenAnswer(i -> {
            Pageable p = i.getArgument(1);
            return new PagedModel<BankaPretragaStavka>(new PageImpl<>(List.<BankaPretragaStavka>of(), p, 0));
        });
    }

    @Test
    void srecanSlucajVracaContentIPage() throws Exception {
        BankaPretragaStavka stavka = new BankaPretragaStavka(11L, 5L, "2 → 10",
                List.of(new GrupaPutanja(3L, "Konverzije"), new GrupaPutanja(5L, "2 → 10")), null,
                StatusVarijante.AKTIVNA, RenderStatus.OK, "Pretvori broj 1011 u dekadni sistem.");
        when(service.pretraga(any(), any())).thenAnswer(i ->
                new PagedModel<>(new PageImpl<>(List.of(stavka), i.<Pageable>getArgument(1), 31)));

        mvc.perform(get("/banka/pretraga").param("predmetId", "7").param("q", "dekadni"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].varijantaId").value(11))
           .andExpect(jsonPath("$.content[0].grupaId").value(5))
           .andExpect(jsonPath("$.content[0].grupaNaziv").value("2 → 10"))
           .andExpect(jsonPath("$.content[0].putanja[0].id").value(3))
           .andExpect(jsonPath("$.content[0].putanja[1].naziv").value("2 → 10"))
           .andExpect(jsonPath("$.content[0].naziv").value(nullValue()))
           .andExpect(jsonPath("$.content[0].status").value("AKTIVNA"))
           .andExpect(jsonPath("$.content[0].renderStatus").value("OK"))
           .andExpect(jsonPath("$.content[0].isecak").value("Pretvori broj 1011 u dekadni sistem."))
           .andExpect(jsonPath("$.page.size").value(25))
           .andExpect(jsonPath("$.page.number").value(0))
           .andExpect(jsonPath("$.page.totalElements").value(31))
           .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void nepoznatoSortPoljeJe400IServisSeNePoziva() throws Exception {
        mvc.perform(get("/banka/pretraga").param("sort", "xyz"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: sort."));
        mvc.perform(get("/banka/pretraga").param("sort", "tekst,asc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: sort."));
        verifyNoInteractions(service);
    }

    @Test
    void kratakUpitJe400IServisSeNePoziva() throws Exception {
        for (String q : List.of("a", "  a ", "", "   ", "😀")) {   // poslednji je jedan znak (dve UTF-16 jedinice)
            mvc.perform(get("/banka/pretraga").param("q", q))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.reason").value(UPIT_KRATAK));
        }
        verifyNoInteractions(service);
    }

    @Test
    void upitOdDvaZnakaProlaziITrimujeSe() throws Exception {
        praznaStrana();
        mvc.perform(get("/banka/pretraga").param("q", "  ab ")).andExpect(status().isOk());
        mvc.perform(get("/banka/pretraga").param("q", "😀😀")).andExpect(status().isOk());
        ArgumentCaptor<BankaFilter> f = ArgumentCaptor.forClass(BankaFilter.class);
        verify(service, times(2)).pretraga(f.capture(), any());
        assertEquals("ab", f.getAllValues().get(0).q());
    }

    @Test
    void nepoznataNamenaIliStatusJe400() throws Exception {
        mvc.perform(get("/banka/pretraga").param("namena", "XYZ"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: namena."));
        mvc.perform(get("/banka/pretraga").param("status", "XYZ"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: status."));
        mvc.perform(get("/banka/pretraga").param("predmetId", "abc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: predmetId."));
        verifyNoInteractions(service);
    }

    @Test
    void filteriIdeuUServis() throws Exception {
        praznaStrana();
        mvc.perform(get("/banka/pretraga").param("predmetId", "7").param("namena", "TEST").param("q", "automat")
                .param("status", "NACRT").param("page", "2").param("size", "10").param("sort", "naziv,asc"))
           .andExpect(status().isOk());
        ArgumentCaptor<BankaFilter> f = ArgumentCaptor.forClass(BankaFilter.class);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(service).pretraga(f.capture(), p.capture());
        assertEquals(new BankaFilter(7L, Namena.TEST, "automat", StatusVarijante.NACRT), f.getValue());
        assertEquals(PageRequest.of(2, 10, Sort.by(Sort.Order.asc("naziv"), Sort.Order.desc("id"))), p.getValue());
    }

    @Test
    void podrazumevano25PoStraniPoIzmenjenoOpadajuce() throws Exception {
        praznaStrana();
        mvc.perform(get("/banka/pretraga")).andExpect(status().isOk());
        ArgumentCaptor<BankaFilter> f = ArgumentCaptor.forClass(BankaFilter.class);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(service).pretraga(f.capture(), p.capture());
        assertEquals(new BankaFilter(null, null, null, null), f.getValue());
        assertEquals(0, p.getValue().getPageNumber());
        assertEquals(25, p.getValue().getPageSize());
        assertEquals(List.of(Sort.Order.desc("izmenjeno"), Sort.Order.desc("id")), p.getValue().getSort().toList());
    }

    @Test
    void velicinaJeOgranicenaNa100() throws Exception {
        praznaStrana();
        mvc.perform(get("/banka/pretraga").param("size", "1000")).andExpect(status().isOk());
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(service).pretraga(any(), p.capture());
        assertEquals(100, p.getValue().getPageSize());
    }
}
