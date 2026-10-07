package tri.novica.gfssystem.rest;

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
import tri.novica.gfssystem.dto.domaci.DomaciFilter;
import tri.novica.gfssystem.dto.domaci.DomaciListItem;
import tri.novica.gfssystem.dto.domaci.DomaciPredavanjeRef;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.student.StudentFilter;
import tri.novica.gfssystem.dto.student.StudentListItem;
import tri.novica.gfssystem.dto.test.TestDetails;
import tri.novica.gfssystem.dto.test.TestFilter;
import tri.novica.gfssystem.dto.test.TestListItem;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.dto.student.pregled.StudentPregledDetails;
import tri.novica.gfssystem.service.DomaciService;
import tri.novica.gfssystem.service.StudentService;
import tri.novica.gfssystem.service.TestService;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Web sloj tri liste (domaći, testovi, studenti): sort whitelist, mapiranje ruta, filteri, oblik odgovora. */
@WebMvcTest({DomaciRest.class, TestRest.class, StudentRest.class})
class PretragaRestTest {

    @Autowired MockMvc mvc;
    @MockitoBean DomaciService domaci;
    @MockitoBean TestService testovi;
    @MockitoBean StudentService studenti;

    // ------------------------------------------------------------------ sort whitelist

    @Test
    void nepoznatSortJe400ZaSveTriListeIServisSeNePoziva() throws Exception {
        for (String putanja : List.of("/domaci/pretraga", "/test/pretraga", "/studenti/pretraga")) {
            mvc.perform(get(putanja).param("sort", "lozinka,asc"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.reason").value("Neispravan parametar: sort."));
        }
        verify(domaci, never()).pretraga(any(), any());
        verify(testovi, never()).pretraga(any(), any());
        verify(studenti, never()).pretraga(any(), any());
    }

    @Test
    void sortPoVezanomEntitetuJe400() throws Exception {
        mvc.perform(get("/studenti/pretraga").param("sort", "grupa.naziv,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/test/pretraga").param("sort", "predmet.naziv,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/domaci/pretraga").param("sort", "text,asc")).andExpect(status().isBadRequest());
    }

    @Test
    void dozvoljenaSortPoljaProlaze() throws Exception {
        mvc.perform(get("/domaci/pretraga").param("sort", "naslov,asc")).andExpect(status().isOk());
        mvc.perform(get("/domaci/pretraga").param("sort", "datum,asc")).andExpect(status().isOk());
        mvc.perform(get("/test/pretraga").param("sort", "maxPoena,desc")).andExpect(status().isOk());
        mvc.perform(get("/test/pretraga").param("sort", "datum,asc")).andExpect(status().isOk());
        for (String polje : List.of("prezime", "ime", "indeks", "godina")) {
            mvc.perform(get("/studenti/pretraga").param("sort", polje + ",asc")).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------ podrazumevano

    @Test
    void podrazumevaniSortDomacihIDatumDesc() throws Exception {
        mvc.perform(get("/domaci/pretraga")).andExpect(status().isOk());
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(domaci).pretraga(any(), p.capture());
        assertEquals(25, p.getValue().getPageSize());
        assertEquals(List.of(Sort.Order.desc("datum"), Sort.Order.desc("id")), p.getValue().getSort().toList());
    }

    @Test
    void podrazumevaniSortTestovaJeDatumDesc() throws Exception {
        mvc.perform(get("/test/pretraga")).andExpect(status().isOk());
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(testovi).pretraga(any(), p.capture());
        assertEquals(List.of(Sort.Order.desc("datum"), Sort.Order.desc("id")), p.getValue().getSort().toList());
    }

    @Test
    void podrazumevaniSortStudenataJePrezimeImeAsc() throws Exception {
        mvc.perform(get("/studenti/pretraga")).andExpect(status().isOk());
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(studenti).pretraga(any(), p.capture());
        assertEquals(List.of(Sort.Order.asc("prezime"), Sort.Order.asc("ime"), Sort.Order.desc("id")),
                p.getValue().getSort().toList());
    }

    @Test
    void velicinaJeOgranicenaNa100() throws Exception {
        mvc.perform(get("/studenti/pretraga").param("size", "1000")).andExpect(status().isOk());
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(studenti).pretraga(any(), p.capture());
        assertEquals(100, p.getValue().getPageSize());
    }

    // ------------------------------------------------------------------ rute i filteri

    @Test
    void testPretragaNijeUhvacenaKaoId() throws Exception {
        mvc.perform(get("/test/pretraga")).andExpect(status().isOk());
        verify(testovi, never()).findById(any());
        verify(testovi).pretraga(any(), any());
    }

    @Test
    void testIdIdaljeIdeNaView() throws Exception {
        when(testovi.findById(5L)).thenReturn(new TestDetails());
        mvc.perform(get("/test/5")).andExpect(status().isOk());
        verify(testovi).findById(5L);
        verify(testovi, never()).pretraga(any(), any());
    }

    @Test
    void studentiPretragaNijeUhvacenaKaoId() throws Exception {
        mvc.perform(get("/studenti/pretraga")).andExpect(status().isOk());
        verify(studenti, never()).findById(any());
        verify(studenti).pretraga(any(), any());
    }

    @Test
    void studentiIdIdaljeIdeNaFindById() throws Exception {
        when(studenti.findById(7L)).thenReturn(new StudentPregledDetails());
        mvc.perform(get("/studenti/7")).andExpect(status().isOk());
        verify(studenti).findById(7L);
        verify(studenti, never()).pretraga(any(), any());
    }

    @Test
    void domaciFilteriIdeuServis() throws Exception {
        mvc.perform(get("/domaci/pretraga")
                .param("predmetId", "1").param("grupaId", "2").param("godina", "2025").param("pregledan", "false")
                .param("q", "integral").param("od", "2025-10-01").param("do", "2025-12-31"))
           .andExpect(status().isOk());
        ArgumentCaptor<DomaciFilter> f = ArgumentCaptor.forClass(DomaciFilter.class);
        verify(domaci).pretraga(f.capture(), any());
        assertEquals(new DomaciFilter(1L, 2L, 2025, false, "integral",
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31)), f.getValue());
    }

    @Test
    void testFilteriIdeuServis() throws Exception {
        mvc.perform(get("/test/pretraga")
                .param("predmetId", "1").param("grupaId", "2").param("godina", "2025").param("pregledan", "true")
                .param("tipTestaId", "9").param("od", "2025-10-01").param("do", "2025-12-31"))
           .andExpect(status().isOk());
        ArgumentCaptor<TestFilter> f = ArgumentCaptor.forClass(TestFilter.class);
        verify(testovi).pretraga(f.capture(), any());
        assertEquals(new TestFilter(1L, 2L, 2025, true, 9L,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31)), f.getValue());
    }

    @Test
    void studentiFilteriIdeuServis() throws Exception {
        mvc.perform(get("/studenti/pretraga").param("grupaId", "2").param("starijiOdGrupe", "3").param("q", "gd 12"))
           .andExpect(status().isOk());
        ArgumentCaptor<StudentFilter> f = ArgumentCaptor.forClass(StudentFilter.class);
        verify(studenti).pretraga(f.capture(), any());
        assertEquals(new StudentFilter(2L, 3L, "gd 12"), f.getValue());
    }

    @Test
    void neispravniParametriSu400() throws Exception {
        mvc.perform(get("/domaci/pretraga").param("godina", "abc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: godina."));
        mvc.perform(get("/test/pretraga").param("tipTestaId", "x"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: tipTestaId."));
        mvc.perform(get("/studenti/pretraga").param("starijiOdGrupe", "x"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: starijiOdGrupe."));
    }

    // ------------------------------------------------------------------ oblik odgovora

    @Test
    void domaciOblikOdgovora() throws Exception {
        PredmetInfo predmet = new PredmetInfo(1L, "Matematika");
        DomaciListItem bezPredavanja = new DomaciListItem(7L, "DZ 1", LocalDate.of(2025, 10, 14), false, predmet,
                null, null, 0, 0);
        DomaciListItem sve = new DomaciListItem(8L, "DZ 2", LocalDate.of(2025, 10, 21), true, predmet,
                new GrupaInfo(2L, "GD-2025", 2025, 30L), new DomaciPredavanjeRef(5L, 3), 12, 30);
        when(domaci.pretraga(any(), any())).thenReturn(new PagedModel<>(
                new PageImpl<>(List.of(bezPredavanja, sve), PageRequest.of(0, 25), 2)));
        mvc.perform(get("/domaci/pretraga"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].predavanje").value(nullValue()))
           .andExpect(jsonPath("$.content[0].grupa").value(nullValue()))
           .andExpect(jsonPath("$.content[1].predavanje.id").value(5))
           .andExpect(jsonPath("$.content[1].predavanje.rb").value(3))
           .andExpect(jsonPath("$.content[1].brojUradjenih").value(12))
           .andExpect(jsonPath("$.content[1].brojStudenata").value(30))
           .andExpect(jsonPath("$.content[1].datum").value("2025-10-21"))
           .andExpect(jsonPath("$.page.totalElements").value(2))
           .andExpect(jsonPath("$.page.size").value(25));
    }

    @Test
    void testOblikOdgovora() throws Exception {
        TestListItem bez = new TestListItem(7L, LocalDate.of(2025, 10, 14), new TipTestaInfo(4L, "Kolokvijum"), 50, false,
                new PredmetInfo(1L, "Matematika"), new GrupaInfo(2L, "GD-2025", 2025, 30L), 0, null, null);
        when(testovi.pretraga(any(), any())).thenReturn(new PagedModel<>(
                new PageImpl<>(List.of(bez), PageRequest.of(0, 25), 1)));
        mvc.perform(get("/test/pretraga"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].tipTesta.naziv").value("Kolokvijum"))
           .andExpect(jsonPath("$.content[0].maxPoena").value(50))
           .andExpect(jsonPath("$.content[0].brojPolaganja").value(0))
           .andExpect(jsonPath("$.content[0].prosek").value(nullValue()))
           .andExpect(jsonPath("$.content[0].procenatProlaznosti").value(nullValue()));
    }

    @Test
    void studentOblikOdgovora() throws Exception {
        StudentListItem bezGrupe = new StudentListItem(1L, "Ana", "Anić", "GD12", 2025, null, null, null);
        when(studenti.pretraga(any(), any())).thenReturn(new PagedModel<>(
                new PageImpl<>(List.of(bezGrupe), PageRequest.of(0, 25), 1)));
        mvc.perform(get("/studenti/pretraga"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].indeks").value("GD12"))
           .andExpect(jsonPath("$.content[0].godina").value(2025))
           .andExpect(jsonPath("$.content[0].email").value(nullValue()))
           .andExpect(jsonPath("$.content[0].grupa").value(nullValue()));
    }
}
