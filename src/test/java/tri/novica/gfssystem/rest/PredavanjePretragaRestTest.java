package tri.novica.gfssystem.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.web.PagedModel;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predavanje.PredavanjeDetails;
import tri.novica.gfssystem.dto.predavanje.PredavanjeFilter;
import tri.novica.gfssystem.dto.predavanje.PredavanjeListItem;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.service.PredavanjeService;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PredavanjeRest.class)
class PredavanjePretragaRestTest {

    @Autowired MockMvc mvc;
    @MockitoBean PredavanjeService service;

    private Pageable uhvatiPageable() {
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(service).pretraga(any(), p.capture());
        return p.getValue();
    }

    @Test
    void nepoznatoSortPoljeJe400IServisSeNePoziva() throws Exception {
        mvc.perform(get("/predavanja/pretraga").param("sort", "lozinka,asc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: sort."));
        verify(service, never()).pretraga(any(), any());
    }

    @Test
    void neispravnaGodinaJe400() throws Exception {
        mvc.perform(get("/predavanja/pretraga").param("godina", "abc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: godina."));
        mvc.perform(get("/predavanja/pretraga").param("od", "2025-13-45"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: od."));
        verify(service, never()).pretraga(any(), any());
    }

    @Test
    void velicinaJeOgranicenaNa100() throws Exception {
        mvc.perform(get("/predavanja/pretraga").param("size", "1000")).andExpect(status().isOk());
        assertEquals(100, uhvatiPageable().getPageSize());
    }

    @Test
    void podrazumevanoStrana0Velicina25SortDatumPaRb() throws Exception {
        mvc.perform(get("/predavanja/pretraga")).andExpect(status().isOk());
        Pageable p = uhvatiPageable();
        assertEquals(0, p.getPageNumber());
        assertEquals(25, p.getPageSize());
        assertEquals(List.of(Sort.Order.desc("datum"), Sort.Order.desc("rb"), Sort.Order.desc("id")), p.getSort().toList());
    }

    @Test
    void filteriIdeuUServis() throws Exception {
        mvc.perform(get("/predavanja/pretraga")
                .param("predmetId", "1").param("grupaId", "2").param("godina", "2025").param("zavrseno", "true")
                .param("q", "integral").param("od", "2025-10-01").param("do", "2025-12-31")
                .param("page", "2").param("size", "10").param("sort", "tema,asc"))
           .andExpect(status().isOk());
        ArgumentCaptor<PredavanjeFilter> f = ArgumentCaptor.forClass(PredavanjeFilter.class);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(service).pretraga(f.capture(), p.capture());
        assertEquals(new PredavanjeFilter(1L, 2L, 2025, true, "integral",
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31)), f.getValue());
        assertEquals(2, p.getValue().getPageNumber());
        assertEquals(10, p.getValue().getPageSize());
        assertEquals(Sort.Order.asc("tema"), p.getValue().getSort().toList().get(0));
    }

    @Test
    void oblikOdgovoraJePagedModel() throws Exception {
        PredavanjeListItem bezGrupe = new PredavanjeListItem(7L, 3, LocalDate.of(2025, 10, 14), "Integrali", true,
                new PredmetInfo(1L, "Matematika"), null, 0, 0, 0);
        PredavanjeListItem saGrupom = new PredavanjeListItem(8L, 4, LocalDate.of(2025, 10, 21), "Izvodi", false,
                new PredmetInfo(1L, "Matematika"), new GrupaInfo(2L, "GD-2025", 2025, 30L), 12, 2, 30);
        when(service.pretraga(any(), any())).thenReturn(new PagedModel<>(
                new PageImpl<>(List.of(bezGrupe, saGrupom), PageRequest.of(0, 25), 2)));
        mvc.perform(get("/predavanja/pretraga"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content.length()").value(2))
           .andExpect(jsonPath("$.content[0].id").value(7))
           .andExpect(jsonPath("$.content[0].datum").value("2025-10-14"))
           .andExpect(jsonPath("$.content[0].grupa").value(org.hamcrest.Matchers.nullValue()))
           .andExpect(jsonPath("$.content[0].predmet.naziv").value("Matematika"))
           .andExpect(jsonPath("$.content[1].grupa.naziv").value("GD-2025"))
           .andExpect(jsonPath("$.content[1].brojPrisutnih").value(12))
           .andExpect(jsonPath("$.content[1].brojStudenata").value(30))
           .andExpect(jsonPath("$.page.size").value(25))
           .andExpect(jsonPath("$.page.number").value(0))
           .andExpect(jsonPath("$.page.totalElements").value(2))
           .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void pretragaNijeUhvacenaKaoId() throws Exception {
        mvc.perform(get("/predavanja/pretraga")).andExpect(status().isOk());
        verify(service, never()).findById(any());
    }

    @Test
    void idIdaljeIdeNaView() throws Exception {
        when(service.findById(5L)).thenReturn(new PredavanjeDetails());
        mvc.perform(get("/predavanja/5")).andExpect(status().isOk());
        verify(service).findById(5L);
        verify(service, never()).pretraga(any(), any());
    }

    @Test
    void propertyReferenceExceptionJe400ANe500() throws Exception {
        // mreža ispod whitelist-e: nepostojeće polje koje stigne do Spring Data-e
        when(service.pretraga(any(), any())).thenThrow(
                new PropertyReferenceException("lozinka", TypeInformation.of(Predavanje.class), List.of()));
        mvc.perform(get("/predavanja/pretraga"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: sort."));
    }
}
