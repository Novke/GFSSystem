package tri.novica.gfssystem.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.domaci.DomaciFilter;
import tri.novica.gfssystem.dto.domaci.DomaciListItem;
import tri.novica.gfssystem.dto.student.StudentFilter;
import tri.novica.gfssystem.dto.student.StudentListItem;
import tri.novica.gfssystem.dto.test.TestFilter;
import tri.novica.gfssystem.dto.test.TestListItem;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.PageableUtil;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pretraga domaćih, testova i studenata nad pravom MySQL bazom (Flyway šema); svaki test se vraća unazad i
 * proverava samo redove koje sam ubaci.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PretragaIT {

    @Autowired DomaciService domaciService;
    @Autowired TestService testService;
    @Autowired StudentService studentService;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    Predmet mat, fiz;
    Grupa ga, gb, gc;
    Student s1, s2, s3, s4, s5;
    Predavanje pred;
    Domaci d1, d2, d3, d4;
    TipTesta kolokvijum, ispit, fizTip;
    tri.novica.gfssystem.entity.Test t1, t2, t3, t4;
    Set<Long> nasiStudenti, nasiDomaci, nasiTestovi;

    @BeforeEach
    void seed() {
        mat = predmet("Matematika PIT");
        fiz = predmet("Fizika PIT");
        ga = grupa("PIT-A", 2023);
        gb = grupa("PIT-B", 2024);
        gc = grupa("PIT-C", 2025);

        s1 = student("Ana", "Anić", "GD12", 2023, ga, "ana@x.rs", "064111");
        s2 = student("Bojan", "Bošković", "GD 13", 2023, ga, null, null);   // star red: razmak u indeksu
        s3 = student("Ceca", "Cvetić", "RN5", 2024, gb, "ceca@x.rs", null);
        s4 = student("Dado", "Dedić", "AR7", 2025, gc, null, null);
        s5 = student("Ena", "Ević", "AR8", 2025, null, null, null);          // bez grupe
        nasiStudenti = Set.of(s1.getId(), s2.getId(), s3.getId(), s4.getId(), s5.getId());

        pred = new Predavanje();
        pred.setPredmet(mat);
        pred.setGrupa(ga);
        pred.setRb(91501);
        pred.setDatum(LocalDate.of(2024, 11, 10));
        em.persist(pred);

        d1 = domaci(mat, ga, pred, LocalDate.of(2024, 11, 10), "Integrali DZ", true);
        d2 = domaci(mat, gb, null, LocalDate.of(2025, 10, 5), "Izvodi 100%", null);
        d3 = domaci(fiz, ga, null, LocalDate.of(2025, 9, 1), "Kinematika", false);
        d4 = domaci(mat, null, null, LocalDate.of(2025, 11, 1), "Stari domaći", false);
        uradjen(s1, d1, false);
        uradjen(s2, d1, true);   // oslobođen se ne računa kao urađen
        uradjen(s3, d2, false);
        nasiDomaci = Set.of(d1.getId(), d2.getId(), d3.getId(), d4.getId());

        kolokvijum = tip("Kolokvijum", mat);
        ispit = tip("Ispit", mat);
        fizTip = tip("Fizika kolokvijum", fiz);
        t1 = test(kolokvijum, mat, ga, LocalDate.of(2024, 11, 20), 50, true);
        t1.setPragProlaza(25);   // jedini test iz seed-a sa pragom; t2-t4 nemaju pa nemaju ni prolaznost
        t2 = test(ispit, mat, gb, LocalDate.of(2025, 10, 15), 100, null);
        t3 = test(fizTip, fiz, ga, LocalDate.of(2025, 9, 15), 30, false);
        t4 = test(kolokvijum, mat, gc, LocalDate.of(2025, 12, 1), 20, false);
        polaganje(t1, s1, 40.0, true);
        polaganje(t1, s2, 20.0, false);
        polaganje(t1, s3, null, null);   // nije izašao: ne ulazi u prosek ni prolaznost
        polaganje(t3, s1, null, null);   // samo bez poena
        polaganje(t4, s4, 10.0, null);   // poeni bez statusa: nije položio
        nasiTestovi = Set.of(t1.getId(), t2.getId(), t3.getId(), t4.getId());
        em.flush();
        em.clear();
    }

    // ================================================================== domaći

    @Test
    void domaciBezPredavanjaVracaPredavanjeNull() {
        List<DomaciListItem> r = domaci(df(mat.getId(), null, null, null, null, null, null), 100).getContent();
        DomaciListItem i2 = nadjiD(r, d2);
        assertNull(i2.getPredavanje());
        assertEquals(gb.getId(), i2.getGrupa().getId());
        DomaciListItem i1 = nadjiD(r, d1);
        assertEquals(pred.getId(), i1.getPredavanje().getId());
        assertEquals(91501, i1.getPredavanje().getRb());
    }

    @Test
    void domaciBezGrupeIBrojaci() {
        List<DomaciListItem> r = domaci(df(mat.getId(), null, null, null, null, null, null), 100).getContent();
        DomaciListItem i4 = nadjiD(r, d4);
        assertNull(i4.getGrupa());
        assertEquals(0, i4.getBrojStudenata());
        assertEquals(0, i4.getBrojUradjenih());
        DomaciListItem i1 = nadjiD(r, d1);
        assertEquals(1, i1.getBrojUradjenih());      // s1; s2 je oslobođen
        assertEquals(2, i1.getBrojStudenata());      // grupa A: s1, s2
        assertEquals(2, i1.getGrupa().getBrojStudenata());
        assertEquals("Integrali DZ", i1.getNaslov());
        assertEquals(LocalDate.of(2024, 11, 10), i1.getDatum());
        assertEquals(Boolean.TRUE, i1.getPregledan());
        assertEquals("Matematika PIT", i1.getPredmet().getNaziv());
        assertEquals(1, nadjiD(r, d2).getBrojUradjenih());
        assertNull(nadjiD(r, d2).getPregledan());
    }

    @Test
    void domaciFilteri() {
        assertEquals(List.of(d4.getId(), d2.getId(), d1.getId()),
                nasiD(domaci(df(mat.getId(), null, null, null, null, null, null), 100)));
        assertEquals(List.of(d3.getId(), d1.getId()),
                nasiD(domaci(df(null, ga.getId(), null, null, null, null, null), 100)));
        // 1. 9. 2025. pripada školskoj 2024/25
        assertEquals(List.of(d3.getId(), d1.getId()), nasiD(domaci(df(null, null, 2024, null, null, null, null), 100)));
        assertEquals(List.of(d4.getId(), d2.getId()), nasiD(domaci(df(null, null, 2025, null, null, null, null), 100)));
        assertEquals(List.of(d1.getId()), nasiD(domaci(df(null, null, null, true, null, null, null), 100)));
        // pregledan = null (stari redovi) se računa kao nepregledan
        assertEquals(List.of(d4.getId(), d2.getId(), d3.getId()),
                nasiD(domaci(df(null, null, null, false, null, null, null), 100)));
        assertEquals(List.of(d1.getId()), nasiD(domaci(df(null, null, null, null, "  INTEGRAL ", null, null), 100)));
        assertEquals(List.of(d2.getId()), nasiD(domaci(df(null, null, null, null, "100%", null, null), 100)));
        assertEquals(List.of(), nasiD(domaci(df(null, null, null, null, "_", null, null), 100)));
        assertEquals(List.of(d2.getId(), d3.getId()),
                nasiD(domaci(df(null, null, null, null, null, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 5)), 100)));
    }

    @Test
    void domaciSortIStranicenje() {
        PagedModel<DomaciListItem> r = domaciS(df(mat.getId(), null, null, null, null, null, null),
                PageRequest.of(0, 2, Sort.by("naslov")));
        assertEquals(List.of(d1.getId(), d2.getId()), r.getContent().stream().map(DomaciListItem::getId).toList());
        assertEquals(3, r.getMetadata().totalElements());
        assertEquals(2, r.getMetadata().totalPages());
    }

    @Test
    void domaciGodinaVanOpsegaJe400() {
        SystemException ex = assertThrows(SystemException.class,
                () -> domaci(df(null, null, 9999, null, null, null, null), 100));
        assertEquals(400, ex.getCode());
    }

    @Test
    void domaciHttpOblikJson() throws Exception {
        mvc.perform(get("/domaci/pretraga").param("predmetId", mat.getId().toString()).param("size", "1000"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.page.size").value(100))
           .andExpect(jsonPath("$.page.totalElements").value(3))
           .andExpect(jsonPath("$.content[0].id").value(d4.getId()))
           .andExpect(jsonPath("$.content[0].grupa").value(nullValue()))
           .andExpect(jsonPath("$.content[0].predavanje").value(nullValue()))
           .andExpect(jsonPath("$.content[2].predavanje.rb").value(91501));
        mvc.perform(get("/domaci/pretraga").param("sort", "text,asc")).andExpect(status().isBadRequest());
    }

    // ================================================================== testovi

    @Test
    void testBezPolaganjaVracaNulu() {
        TestListItem i2 = nadjiT(test(tf(mat.getId(), null, null, null, null, null, null), 100).getContent(), t2);
        assertEquals(0, i2.getBrojPolaganja());
        assertNull(i2.getProsek());
        assertNull(i2.getProcenatProlaznosti());
        assertEquals(100, i2.getMaxPoena());
        assertEquals("Ispit", i2.getTipTesta().getNaziv());
        assertEquals(gb.getId(), i2.getGrupa().getId());
        assertNull(i2.getPregledan());
    }

    @Test
    void testProsekIProlaznostSamoOdPolaganjaSaPoenima() {
        List<TestListItem> r = test(tf(null, null, null, null, null, null, null), 100).getContent();
        TestListItem i1 = nadjiT(r, t1);
        assertEquals(3, i1.getBrojPolaganja());
        assertEquals(30.0, i1.getProsek(), 1e-9);               // (40 + 20) / 2, treće polaganje je bez poena
        assertEquals(50.0, i1.getProcenatProlaznosti(), 1e-9);  // 1 od 2
        assertEquals(Boolean.TRUE, i1.getPregledan());

        TestListItem i3 = nadjiT(r, t3);                         // samo polaganje bez poena
        assertEquals(1, i3.getBrojPolaganja());
        assertNull(i3.getProsek());
        assertNull(i3.getProcenatProlaznosti());

        TestListItem i4 = nadjiT(r, t4);                         // poeni, ali bez praga prolaza: nema prolaznosti
        assertEquals(1, i4.getBrojPolaganja());
        assertEquals(10.0, i4.getProsek(), 1e-9);
        assertNull(i4.getPragProlaza());
        assertNull(i4.getProcenatProlaznosti());
        assertEquals(25, i1.getPragProlaza());
    }

    @Test
    void testProlazPoPragu() {
        tri.novica.gfssystem.entity.Test t = test(em.find(TipTesta.class, kolokvijum.getId()), mat, gb, LocalDate.of(2025, 12, 5), 40, false);
        t.setPragProlaza(20);
        Student s6 = student("Fedja", "Fedić", "GD61", 2024, gb, null, null);
        Student s7 = student("Gaga", "Gagić", "GD62", 2024, gb, null, null);
        Student s8 = student("Hana", "Hanić", "GD63", 2024, gb, null, null);
        polaganje(t, s1, 20.0, null);                        // tačno na pragu: prolazi (polozio = null)
        polaganje(t, s2, 19.5, true);                        // ispod praga: pada iako je polozio = true
        polaganje(t, s3, 40.0, true).setPrepisivao(true);    // prepisivao: pada uprkos poenima
        polaganje(t, s6, null, true);                        // bez poena: ne ulazi ni u imenilac
        polaganje(t, s7, 30.0, false);                       // iznad praga: prolazi iako je polozio = false
        polaganje(t, s8, 0.0, true);                         // nula poena: pada
        em.flush();
        em.clear();
        TestListItem i = nadjiT(test(tf(null, null, null, null, null, null, null), 100).getContent(), t);
        assertEquals(6, i.getBrojPolaganja());
        assertEquals(20, i.getPragProlaza());
        assertEquals(40.0, i.getProcenatProlaznosti(), 1e-9);   // 2 od 5 sa poenima
    }

    @Test
    void testProcenatProlaznostiZaokruzenNaDveDecimale() {
        tri.novica.gfssystem.entity.Test t = test(em.find(TipTesta.class, kolokvijum.getId()), mat, gb, LocalDate.of(2025, 12, 8), 40, false);
        t.setPragProlaza(10);
        polaganje(t, s1, 10.0, null);
        polaganje(t, s2, 30.0, null);
        polaganje(t, s3, 9.0, null);
        em.flush();
        em.clear();
        TestListItem i = nadjiT(test(tf(null, null, null, null, null, null, null), 100).getContent(), t);
        assertEquals(66.67, i.getProcenatProlaznosti(), 1e-12);   // 2 od 3, ne 66.666...
    }

    @Test
    void testPragNulaSvimaSaPoenimaProlaze() {
        tri.novica.gfssystem.entity.Test t = test(em.find(TipTesta.class, kolokvijum.getId()), mat, gb, LocalDate.of(2025, 12, 6), 40, false);
        t.setPragProlaza(0);
        polaganje(t, s1, 0.0, null);
        polaganje(t, s2, null, null);
        em.flush();
        em.clear();
        TestListItem i = nadjiT(test(tf(null, null, null, null, null, null, null), 100).getContent(), t);
        assertEquals(100.0, i.getProcenatProlaznosti(), 1e-9);
    }

    @Test
    void testPragSamoPrepisivaciIliBezPoenaDajeNulaOdsto() {
        tri.novica.gfssystem.entity.Test t = test(em.find(TipTesta.class, kolokvijum.getId()), mat, gb, LocalDate.of(2025, 12, 7), 40, false);
        t.setPragProlaza(10);
        polaganje(t, s1, 30.0, true).setPrepisivao(true);
        em.flush();
        em.clear();
        TestListItem i = nadjiT(test(tf(null, null, null, null, null, null, null), 100).getContent(), t);
        assertEquals(0.0, i.getProcenatProlaznosti(), 1e-9);
    }

    @Test
    void testDetaljiProlaznostPoPragu() {
        var d = testService.findById(t1.getId());
        assertEquals(25, d.getPragProlaza());
        assertEquals(1, d.getStatistika().getBrojPolozenih());
        assertEquals(1, d.getStatistika().getBrojPalih());
        assertEquals(50.0, d.getStatistika().getProcenatProlaznosti(), 1e-9);

        var bez = testService.findById(t4.getId());
        assertNull(bez.getPragProlaza());
        assertNull(bez.getStatistika().getBrojPolozenih());
        assertNull(bez.getStatistika().getBrojPalih());
        assertNull(bez.getStatistika().getProcenatProlaznosti());
    }

    @Test
    void testFilteri() {
        assertEquals(List.of(t4.getId(), t2.getId(), t1.getId()),
                nasiT(test(tf(mat.getId(), null, null, null, null, null, null), 100)));
        assertEquals(List.of(t3.getId(), t1.getId()),
                nasiT(test(tf(null, ga.getId(), null, null, null, null, null), 100)));
        // 15. 9. 2025. pripada školskoj 2024/25
        assertEquals(List.of(t3.getId(), t1.getId()), nasiT(test(tf(null, null, 2024, null, null, null, null), 100)));
        assertEquals(List.of(t4.getId(), t2.getId()), nasiT(test(tf(null, null, 2025, null, null, null, null), 100)));
        assertEquals(List.of(t1.getId()), nasiT(test(tf(null, null, null, true, null, null, null), 100)));
        assertEquals(List.of(t4.getId(), t2.getId(), t3.getId()),
                nasiT(test(tf(null, null, null, false, null, null, null), 100)));
        assertEquals(List.of(t4.getId(), t1.getId()),
                nasiT(test(tf(null, null, null, null, kolokvijum.getId(), null, null), 100)));
        assertEquals(List.of(t2.getId(), t3.getId()),
                nasiT(test(tf(null, null, null, null, null, LocalDate.of(2025, 9, 15), LocalDate.of(2025, 10, 15)), 100)));
    }

    @Test
    void testSortPoMaxPoena() {
        PagedModel<TestListItem> r = testS(tf(mat.getId(), null, null, null, null, null, null),
                PageRequest.of(0, 100, Sort.by(Sort.Order.desc("maxPoena"))));
        assertEquals(List.of(t2.getId(), t1.getId(), t4.getId()), r.getContent().stream().map(TestListItem::getId).toList());
    }

    @Test
    void testHttp() throws Exception {
        mvc.perform(get("/test/pretraga").param("predmetId", mat.getId().toString()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.page.totalElements").value(3))
           .andExpect(jsonPath("$.content[1].id").value(t2.getId()))
           .andExpect(jsonPath("$.content[1].brojPolaganja").value(0))
           .andExpect(jsonPath("$.content[1].prosek").value(nullValue()))
           .andExpect(jsonPath("$.content[1].tipTesta.naziv").value("Ispit"));
        mvc.perform(get("/test/pretraga").param("sort", "grupe,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/test/" + t2.getId())).andExpect(status().isOk());   // /{id} i dalje radi
    }

    @Test
    void testPatchPragProlazaHttp() throws Exception {
        // t1 je pregledan (PUT ga ne dira), a PATCH prag sme
        String url = "/test/" + t1.getId() + "/prag-prolaza";
        patch(url, "{\"pragProlaza\":20}").andExpect(status().isOk())
                .andExpect(jsonPath("$.pragProlaza").value(20))
                .andExpect(jsonPath("$.statistika.brojPolozenih").value(2));   // 40 i 20 poena, prag je uključen
        patch(url, "{\"pragProlaza\":50}").andExpect(status().isOk())        // prag == max
                .andExpect(jsonPath("$.statistika.brojPolozenih").value(0));
        patch(url, "{\"pragProlaza\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.pragProlaza").value(nullValue()))
                .andExpect(jsonPath("$.statistika.procenatProlaznosti").value(nullValue()));
        String poruka = "Prag prolaza mora biti između 0 i maksimalnog broja poena.";
        patch(url, "{\"pragProlaza\":51}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value(poruka));
        patch(url, "{\"pragProlaza\":-1}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value(poruka));
        patch("/test/999999999/prag-prolaza", "{\"pragProlaza\":1}").andExpect(status().isNotFound());
    }

    @Test
    void tipoviPredmetaSviOpcionoPrikazujeNeaktivne() throws Exception {
        em.find(TipTesta.class, ispit.getId()).setAktivan(false);
        em.flush();
        em.clear();
        String url = "/predmeti/" + mat.getId() + "/tipovi";
        mvc.perform(get(url)).andExpect(status().isOk())                       // podrazumevano samo aktivni
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].naziv").value("Kolokvijum"))
                .andExpect(jsonPath("$[0].aktivan").value(true));
        mvc.perform(get(url).param("svi", "false")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(url).param("svi", "true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.naziv == 'Ispit')].aktivan").value(false))
                .andExpect(jsonPath("$[?(@.naziv == 'Kolokvijum')].aktivan").value(true));
        mvc.perform(get("/predmeti/999999999/tipovi").param("svi", "true")).andExpect(status().isNotFound());
    }

    @Test
    void testPutNeMenjaPrag() throws Exception {
        var t = em.find(tri.novica.gfssystem.entity.Test.class, t2.getId());   // t2 je odvojen posle seed-a
        t.setPragProlaza(30);   // max 100, nepregledan (TestPP ne dozvoljava PUT sa pregledan = null)
        t.setPregledan(false);
        t.setGrupe(Set.of(TestGrupa.A));
        em.flush();
        em.clear();
        mvc.perform(put("/test/" + t2.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datum\":\"2025-10-15\",\"maxPoena\":100,\"tipTestaId\":" + ispit.getId()
                                + ",\"pragProlaza\":80}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.pragProlaza").value(30));   // prag iz tela se ignoriše
        mvc.perform(put("/test/" + t2.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datum\":\"2025-10-15\",\"maxPoena\":100,\"tipTestaId\":" + ispit.getId() + "}"))
           .andExpect(jsonPath("$.pragProlaza").value(30));   // izostavljen prag ga ne briše
    }

    @Test
    void studentDetaljiSadrzeGodinuKontaktIGrupu() throws Exception {
        Student s = em.find(Student.class, s1.getId());   // s1 je odvojen posle seed-a
        s.setDatumRodjenja(LocalDate.of(2005, 3, 14));
        s.setOpstina("Subotica");
        em.flush();
        em.clear();
        mvc.perform(get("/studenti/" + s1.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.godina").value(2023))
                .andExpect(jsonPath("$.email").value("ana@x.rs"))
                .andExpect(jsonPath("$.brojTelefona").value("064111"))
                .andExpect(jsonPath("$.grupaId").value(ga.getId()))
                .andExpect(jsonPath("$.grupa").value("PIT-A"))
                .andExpect(jsonPath("$.datumRodjenja").value("2005-03-14"))
                .andExpect(jsonPath("$.opstina").value("Subotica"));
        mvc.perform(get("/studenti/" + s5.getId())).andExpect(status().isOk())   // bez grupe
                .andExpect(jsonPath("$.godina").value(2025))
                .andExpect(jsonPath("$.grupaId").value(nullValue()))
                .andExpect(jsonPath("$.grupa").value(nullValue()))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.datumRodjenja").value(nullValue()));
    }

    private org.springframework.test.web.servlet.ResultActions patch(String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ================================================================== studenti

    @Test
    void starijiOdGrupeVracaSamoStudenteIzStarijihGrupa() {
        assertEquals(List.of(s1.getId(), s2.getId(), s3.getId()),
                nasiS(student(sf(null, gc.getId(), null), 100)));                  // 2023, 2024 < 2025
        assertEquals(List.of(s1.getId(), s2.getId()), nasiS(student(sf(null, gb.getId(), null), 100)));
        assertEquals(List.of(), nasiS(student(sf(null, ga.getId(), null), 100)));  // nijedna grupa nije starija od 2023
    }

    @Test
    void starijiOdGrupeSeKombinujeSaGrupom() {
        assertEquals(List.of(s3.getId()), nasiS(student(sf(gb.getId(), gc.getId(), null), 100)));
        assertEquals(List.of(), nasiS(student(sf(gc.getId(), gc.getId(), null), 100)));
    }

    @Test
    void starijiOdGrupeNepostojecaGrupaJe404() {
        SystemException ex = assertThrows(SystemException.class, () -> student(sf(null, 999_999_999L, null), 100));
        assertEquals(404, ex.getCode());
        assertEquals("Grupa ne postoji! ID = 999999999", ex.getMessage());
    }

    @Test
    void qSaRazmakomNalaziIndeksBezRazmaka() {
        assertEquals(List.of(s1.getId()), nasiS(student(sf(null, null, "gd 12"), 100)));
        assertEquals(List.of(s1.getId()), nasiS(student(sf(null, null, "GD12"), 100)));
        assertEquals(List.of(s1.getId()), nasiS(student(sf(null, null, "  Gd  12 "), 100)));
    }

    @Test
    void qNalaziIndeksSaRazmakomUBazi() {
        assertEquals(List.of(s2.getId()), nasiS(student(sf(null, null, "gd13"), 100)));
        assertEquals(List.of(s2.getId()), nasiS(student(sf(null, null, "gd 13"), 100)));
    }

    @Test
    void qTraziUImenuPrezimenuIPunomImenu() {
        assertEquals(List.of(s1.getId()), nasiS(student(sf(null, null, "ana"), 100)));
        assertEquals(List.of(s2.getId()), nasiS(student(sf(null, null, "BOŠKOVIĆ"), 100)));
        assertEquals(List.of(s3.getId()), nasiS(student(sf(null, null, "ceca cvetić"), 100)));
        assertEquals(List.of(s4.getId()), nasiS(student(sf(null, null, "o dedić"), 100)));
        assertEquals(List.of(), nasiS(student(sf(null, null, "_"), 100)));
        assertEquals(List.of(), nasiS(student(sf(null, null, "%"), 100)));
    }

    @Test
    void qNalaziIndeksSaNeprekidivimRazmakom() {
        assertEquals(List.of(s1.getId()), nasiS(student(sf(null, null, "gd 12"), 100)));
    }

    @Test
    void prazanQJeBezFiltera() {
        assertEquals(5, nasiS(student(sf(null, null, "   "), 100)).size());
    }

    @Test
    void studentiPodrazumevaniSortJePrezimeIme() {
        assertEquals(List.of(s1.getId(), s2.getId(), s3.getId(), s4.getId(), s5.getId()),
                nasiS(student(sf(null, null, null), 100)));
    }

    @Test
    void studentiSortPoGodiniOpadajuceIStranicenje() {
        PagedModel<StudentListItem> r = studentS(sf(null, null, "x"), PageRequest.of(0, 100));  // proba praznog rezultata
        assertNotNull(r);
        PagedModel<StudentListItem> g = studentS(sf(gb.getId(), null, null), PageRequest.of(0, 100, Sort.by("godina").descending()));
        assertEquals(List.of(s3.getId()), g.getContent().stream().map(StudentListItem::getId).toList());
        PagedModel<StudentListItem> p = studentS(sf(ga.getId(), null, null), PageRequest.of(1, 1, Sort.by("ime")));
        assertEquals(List.of(s2.getId()), p.getContent().stream().map(StudentListItem::getId).toList());
        assertEquals(2, p.getMetadata().totalElements());
        assertEquals(2, p.getMetadata().totalPages());
    }

    @Test
    void studentMapiranjeIRupe() {
        List<StudentListItem> r = student(sf(null, null, null), 100).getContent();
        StudentListItem i1 = nadjiS(r, s1);
        assertEquals("Ana", i1.getIme());
        assertEquals("Anić", i1.getPrezime());
        assertEquals("GD12", i1.getIndeks());
        assertEquals(2023, i1.getGodina());
        assertEquals("ana@x.rs", i1.getEmail());
        assertEquals("064111", i1.getBrojTelefona());
        assertEquals("PIT-A", i1.getGrupa().getNaziv());
        assertEquals(2023, i1.getGrupa().getGodinaUpisa());
        assertEquals(2, i1.getGrupa().getBrojStudenata());
        StudentListItem i5 = nadjiS(r, s5);
        assertNull(i5.getGrupa());
        assertNull(i5.getEmail());
        assertNull(i5.getBrojTelefona());
    }

    @Test
    void studentiHttp() throws Exception {
        mvc.perform(get("/studenti/pretraga").param("grupaId", ga.getId().toString()).param("q", "gd 12"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.page.totalElements").value(1))
           .andExpect(jsonPath("$.content[0].indeks").value("GD12"))
           .andExpect(jsonPath("$.content[0].grupa.naziv").value("PIT-A"));
        mvc.perform(get("/studenti/pretraga").param("starijiOdGrupe", "999999999"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Grupa ne postoji! ID = 999999999"));
        mvc.perform(get("/studenti/pretraga").param("sort", "grupa.naziv")).andExpect(status().isBadRequest());
        mvc.perform(get("/studenti/" + s1.getId())).andExpect(status().isOk());   // /{id} i dalje radi
    }

    // ================================================================== pomoćno

    private PagedModel<DomaciListItem> domaci(DomaciFilter f, int size) {
        return domaciS(f, PageRequest.of(0, size));
    }

    private PagedModel<DomaciListItem> domaciS(DomaciFilter f, Pageable p) {
        return domaciService.pretraga(f, PageableUtil.proveri(p, DomaciService.SORT_POLJA, DomaciService.PODRAZUMEVANI_SORT));
    }

    private PagedModel<TestListItem> test(TestFilter f, int size) {
        return testS(f, PageRequest.of(0, size));
    }

    private PagedModel<TestListItem> testS(TestFilter f, Pageable p) {
        return testService.pretraga(f, PageableUtil.proveri(p, TestService.SORT_POLJA, TestService.PODRAZUMEVANI_SORT));
    }

    private PagedModel<StudentListItem> student(StudentFilter f, int size) {
        return studentS(f, PageRequest.of(0, size));
    }

    private PagedModel<StudentListItem> studentS(StudentFilter f, Pageable p) {
        return studentService.pretraga(f, PageableUtil.proveri(p, StudentService.SORT_POLJA, StudentService.PODRAZUMEVANI_SORT));
    }

    private static DomaciFilter df(Long predmet, Long grupa, Integer godina, Boolean pregledan, String q, LocalDate od, LocalDate doD) {
        return new DomaciFilter(predmet, grupa, godina, pregledan, q, od, doD);
    }

    private static TestFilter tf(Long predmet, Long grupa, Integer godina, Boolean pregledan, Long tip, LocalDate od, LocalDate doD) {
        return new TestFilter(predmet, grupa, godina, pregledan, tip, od, doD);
    }

    private static StudentFilter sf(Long grupa, Long stariji, String q) {
        return new StudentFilter(grupa, stariji, q);
    }

    private List<Long> nasiD(PagedModel<DomaciListItem> r) {
        return r.getContent().stream().map(DomaciListItem::getId).filter(nasiDomaci::contains).toList();
    }

    private List<Long> nasiT(PagedModel<TestListItem> r) {
        return r.getContent().stream().map(TestListItem::getId).filter(nasiTestovi::contains).toList();
    }

    private List<Long> nasiS(PagedModel<StudentListItem> r) {
        return r.getContent().stream().map(StudentListItem::getId).filter(nasiStudenti::contains).toList();
    }

    private static DomaciListItem nadjiD(List<DomaciListItem> r, Domaci d) {
        return r.stream().filter(i -> i.getId().equals(d.getId())).findFirst().orElseThrow();
    }

    private static TestListItem nadjiT(List<TestListItem> r, tri.novica.gfssystem.entity.Test t) {
        return r.stream().filter(i -> i.getId().equals(t.getId())).findFirst().orElseThrow();
    }

    private static StudentListItem nadjiS(List<StudentListItem> r, Student s) {
        return r.stream().filter(i -> i.getId().equals(s.getId())).findFirst().orElseThrow();
    }

    private Predmet predmet(String naziv) {
        Predmet p = new Predmet();
        p.setNaziv(naziv);
        em.persist(p);
        return p;
    }

    private Grupa grupa(String naziv, int godinaUpisa) {
        Grupa g = new Grupa();
        g.setNaziv(naziv);
        g.setGodinaUpisa(godinaUpisa);
        em.persist(g);
        return g;
    }

    private Student student(String ime, String prezime, String indeks, int godina, Grupa g, String email, String telefon) {
        Student s = new Student();
        s.setIme(ime);
        s.setPrezime(prezime);
        s.setIndeks(indeks);
        s.setGodina(godina);
        s.setGrupa(g);
        s.setEmail(email);
        s.setBrojTelefona(telefon);
        em.persist(s);
        return s;
    }

    private Domaci domaci(Predmet p, Grupa g, Predavanje pr, LocalDate datum, String naslov, Boolean pregledan) {
        Domaci d = new Domaci();
        d.setPredmet(p);
        d.setGrupa(g);
        d.setPredavanje(pr);
        d.setDatum(datum);
        d.setNaslov(naslov);
        d.setPregledan(pregledan);
        em.persist(d);
        return d;
    }

    private void uradjen(Student s, Domaci d, boolean oslobodjen) {
        UradjenDomaci u = new UradjenDomaci();
        u.setStudent(s);
        u.setDomaci(d);
        u.setOslobodjen(oslobodjen);
        em.persist(u);
    }

    private TipTesta tip(String naziv, Predmet p) {
        TipTesta t = new TipTesta(naziv, p);
        em.persist(t);
        return t;
    }

    private tri.novica.gfssystem.entity.Test test(TipTesta tip, Predmet p, Grupa g, LocalDate datum, int max, Boolean pregledan) {
        tri.novica.gfssystem.entity.Test t = new tri.novica.gfssystem.entity.Test();
        t.setTipTesta(tip);
        t.setPredmet(p);
        t.setGrupa(g);
        t.setDatum(datum);
        t.setMaxPoena(max);
        t.setPregledan(pregledan);
        em.persist(t);
        return t;
    }

    private Polaganje polaganje(tri.novica.gfssystem.entity.Test t, Student s, Double poeni, Boolean polozio) {
        Polaganje p = Polaganje.defaultPolaganje(t, s);
        p.setOstvareniPoeni(poeni);
        p.setPolozio(polozio);
        em.persist(p);
        return p;
    }
}
