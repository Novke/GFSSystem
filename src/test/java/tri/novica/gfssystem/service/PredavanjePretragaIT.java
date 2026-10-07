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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.predavanje.PredavanjeFilter;
import tri.novica.gfssystem.dto.predavanje.PredavanjeListItem;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.PageableUtil;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pretraga predavanja nad pravom MySQL bazom (Flyway šema, {@code SPRING_DATASOURCE_URL}); svaki test se vraća
 * unazad. Provere su ograničene na redove koje test sam ubaci, pa ne zavise od drugih podataka u bazi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PredavanjePretragaIT {

    @Autowired PredavanjeService service;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    Predmet mat, fiz;
    Grupa a, b;
    Student s1, s2, s3, s4, s5;
    /** p1: mat/A 2024-11-05 završeno; p2: mat/B 2025-10-01; p3: fiz/A 2025-09-30 zavrseno=null; p4: mat/bez grupe 2025-12-01. */
    Predavanje p1, p2, p3, p4;
    Set<Long> nasi;

    @BeforeEach
    void seed() {
        mat = predmet("Matematika IT");
        fiz = predmet("Fizika IT");
        a = grupa("IT-A", 2024);
        b = grupa("IT-B", 2025);
        s1 = student("ITA1", a);
        s2 = student("ITA2", a);
        s3 = student("ITA3", a);
        s4 = student("ITB1", b);
        s5 = student("ITB2", b);
        p1 = predavanje(mat, a, 91001, LocalDate.of(2024, 11, 5), "Uvod u Integrale", true);
        p2 = predavanje(mat, b, 91002, LocalDate.of(2025, 10, 1), "Izvodi 100%", false);
        p3 = predavanje(fiz, a, 91003, LocalDate.of(2025, 9, 30), "Kinematika i integrali", null);
        p4 = predavanje(mat, null, 91004, LocalDate.of(2025, 12, 1), "Stari čas", true);
        aktivnost(p1, s1, TipAktivnosti.PRISUSTVO);
        aktivnost(p1, s1, TipAktivnosti.ZADATAK); // duplikat iz starih podataka: student se broji jednom
        aktivnost(p1, s2, TipAktivnosti.ZADATAK);
        aktivnost(p2, s4, TipAktivnosti.SA_ZVEZDICOM);
        aktivnost(p4, s3, TipAktivnosti.PRISUSTVO);
        em.flush();
        em.clear();
        nasi = Set.of(p1.getId(), p2.getId(), p3.getId(), p4.getId());
    }

    // ------------------------------------------------------------------ filteri

    @Test
    void filterPoPredmetuSortiranoPoDatumuOpadajuce() {
        PagedModel<PredavanjeListItem> r = pretraga(filter(mat.getId(), null, null, null, null, null, null), strana());
        assertEquals(List.of(p4.getId(), p2.getId(), p1.getId()), ids(r));
        assertEquals(3, r.getMetadata().totalElements());
    }

    @Test
    void filterPoGrupi() {
        assertEquals(List.of(p3.getId(), p1.getId()), ids(pretraga(filter(null, a.getId(), null, null, null, null, null), strana())));
    }

    @Test
    void filterPoSkolskojGodini() {
        assertEquals(List.of(p3.getId(), p1.getId()), nasiIds(pretraga(filter(null, null, 2024, null, null, null, null), strana())));
        assertEquals(List.of(p4.getId(), p2.getId()), nasiIds(pretraga(filter(null, null, 2025, null, null, null, null), strana())));
    }

    @Test
    void qJeCaseInsensitiveIOdsecaRazmake() {
        assertEquals(List.of(p3.getId(), p1.getId()), nasiIds(pretraga(filter(null, null, null, null, "  INTEGRAL ", null, null), strana())));
    }

    @Test
    void qSaProcentomJeDoslovan() {
        assertEquals(List.of(p2.getId()), nasiIds(pretraga(filter(null, null, null, null, "100%", null, null), strana())));
        assertEquals(List.of(p2.getId()), nasiIds(pretraga(filter(null, null, null, null, "%", null, null), strana())));
        // "_" bi bez escape-a bio bilo koji znak ("Stari čas", "Izvodi 100%", ...)
        assertEquals(List.of(), nasiIds(pretraga(filter(null, null, null, null, "_", null, null), strana())));
    }

    @Test
    void prazanQJeBezFiltera() {
        assertEquals(3, pretraga(filter(mat.getId(), null, null, null, "   ", null, null), strana()).getContent().size());
    }

    @Test
    void filterZavrseno() {
        assertEquals(List.of(p4.getId(), p1.getId()), nasiIds(pretraga(filter(null, null, null, true, null, null, null), strana())));
        // zavrseno = null (stari redovi) se računa kao nezavršeno, kao podrazumevana vrednost u entitetu
        assertEquals(List.of(p2.getId(), p3.getId()), nasiIds(pretraga(filter(null, null, null, false, null, null, null), strana())));
    }

    @Test
    void opsegDatumaUkljucujeGranice() {
        PredavanjeFilter f = filter(mat.getId(), null, null, null, null, LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 1));
        assertEquals(List.of(p4.getId(), p2.getId()), ids(pretraga(f, strana())));
    }

    @Test
    void sortDatumRastuce() {
        Pageable p = PageableUtil.proveri(PageRequest.of(0, 25, Sort.by("datum")), PredavanjeService.SORT_POLJA,
                PredavanjeService.PODRAZUMEVANI_SORT);
        assertEquals(List.of(p1.getId(), p2.getId(), p4.getId()), ids(pretraga(filter(mat.getId(), null, null, null, null, null, null), p)));
    }

    @Test
    void stranicenje() {
        PredavanjeFilter f = filter(mat.getId(), null, null, null, null, null, null);
        Pageable prva = PageableUtil.proveri(PageRequest.of(0, 2), PredavanjeService.SORT_POLJA, PredavanjeService.PODRAZUMEVANI_SORT);
        PagedModel<PredavanjeListItem> r = pretraga(f, prva);
        assertEquals(List.of(p4.getId(), p2.getId()), ids(r));
        assertEquals(3, r.getMetadata().totalElements());
        assertEquals(2, r.getMetadata().totalPages());
        assertEquals(List.of(p1.getId()), ids(pretraga(f, prva.next())));
    }

    @Test
    void godinaVanOpsegaJe400() {
        SystemException ex = assertThrows(SystemException.class,
                () -> pretraga(filter(null, null, Integer.MAX_VALUE, null, null, null, null), strana()));
        assertEquals(400, ex.getCode());
    }

    // ------------------------------------------------------------------ brojači i mapiranje

    @Test
    void brojaciIMapiranje() {
        List<PredavanjeListItem> r = pretraga(filter(null, null, null, null, null, null, null), strana())
                .getContent().stream().filter(i -> nasi.contains(i.getId())).toList();
        PredavanjeListItem i1 = nadji(r, p1), i2 = nadji(r, p2), i3 = nadji(r, p3), i4 = nadji(r, p4);

        assertEquals(2, i1.getBrojPrisutnih());
        assertEquals(3, i1.getBrojStudenata());
        assertEquals(1, i2.getBrojPrisutnih());
        assertEquals(2, i2.getBrojStudenata());
        assertEquals(0, i3.getBrojPrisutnih());
        assertEquals(3, i3.getBrojStudenata());

        assertEquals(91001, i1.getRb());
        assertEquals(LocalDate.of(2024, 11, 5), i1.getDatum());
        assertEquals("Uvod u Integrale", i1.getTema());
        assertEquals(Boolean.TRUE, i1.getZavrseno());
        assertEquals(mat.getId(), i1.getPredmet().getId());
        assertEquals("Matematika IT", i1.getPredmet().getNaziv());
        assertEquals(a.getId(), i1.getGrupa().getId());
        assertEquals("IT-A", i1.getGrupa().getNaziv());
        assertEquals(2024, i1.getGrupa().getGodinaUpisa());
        assertNull(i3.getZavrseno());
    }

    @Test
    void predavanjeBezGrupe() {
        PredavanjeListItem i4 = nadji(pretraga(filter(mat.getId(), null, null, null, null, null, null), strana()).getContent(), p4);
        assertNull(i4.getGrupa());
        assertEquals(0, i4.getBrojStudenata());
        assertEquals(1, i4.getBrojPrisutnih());
    }

    @Test
    void brojStarijihPrisutnihSamoIzVanGrupePredavanja() {
        Student bezGrupe = student("ITX1", null);
        aktivnost(p2, s1, TipAktivnosti.PRISUSTVO);      // grupa A na predavanju grupe B: stariji
        aktivnost(p2, s1, TipAktivnosti.ZADATAK);        // duplikat: broji se jednom
        aktivnost(p2, s5, TipAktivnosti.PRISUSTVO);      // grupa B: nije stariji
        aktivnost(p1, bezGrupe, TipAktivnosti.PRISUSTVO); // bez grupe: van grupe predavanja
        em.flush();
        em.clear();
        List<PredavanjeListItem> lista = pretraga(filter(null, null, null, null, null, null, null), strana()).getContent();
        PredavanjeListItem i1 = nadji(lista, p1);
        assertEquals(3, i1.getBrojPrisutnih());           // s1, s2 + bez grupe
        assertEquals(1, i1.getBrojStarijihPrisutnih());   // samo student bez grupe
        PredavanjeListItem i2 = nadji(lista, p2);
        assertEquals(3, i2.getBrojPrisutnih());           // s4, s1, s5
        assertEquals(1, i2.getBrojStarijihPrisutnih());   // s1
        assertEquals(0, nadji(lista, p3).getBrojStarijihPrisutnih());   // niko nije prisutan
        PredavanjeListItem i4 = nadji(lista, p4);
        assertEquals(1, i4.getBrojPrisutnih());
        assertEquals(0, i4.getBrojStarijihPrisutnih());   // predavanje bez grupe: nema sa čim da se poredi
    }

    @Test
    void praznaStrana() {
        PagedModel<PredavanjeListItem> r = pretraga(filter(-1L, null, null, null, null, null, null), strana());
        assertTrue(r.getContent().isEmpty());
        assertEquals(0, r.getMetadata().totalElements());
    }

    // ------------------------------------------------------------------ ceo HTTP put (prava konfiguracija Spring Data web-a)

    @Test
    void httpVelicinaOgranicenaIOblikJson() throws Exception {
        mvc.perform(get("/predavanja/pretraga").param("predmetId", mat.getId().toString()).param("size", "1000"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.page.size").value(100))
           .andExpect(jsonPath("$.page.number").value(0))
           .andExpect(jsonPath("$.page.totalElements").value(3))
           .andExpect(jsonPath("$.page.totalPages").value(1))
           .andExpect(jsonPath("$.content.length()").value(3))
           .andExpect(jsonPath("$.content[0].id").value(p4.getId()))
           .andExpect(jsonPath("$.content[0].grupa").value(nullValue()))
           .andExpect(jsonPath("$.content[0].brojStudenata").value(0))
           .andExpect(jsonPath("$.content[0].brojStarijihPrisutnih").value(0))
           .andExpect(jsonPath("$.content[0].datum").value("2025-12-01"))
           .andExpect(jsonPath("$.content[2].grupa.naziv").value("IT-A"));
    }

    @Test
    void httpPodrazumevanaVelicina25() throws Exception {
        mvc.perform(get("/predavanja/pretraga").param("predmetId", mat.getId().toString()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.page.size").value(25));
    }

    @Test
    void httpNepoznatSortJe400() throws Exception {
        mvc.perform(get("/predavanja/pretraga").param("sort", "predmet.naziv,asc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: sort."));
    }

    // ------------------------------------------------------------------ pomoćno

    private PagedModel<PredavanjeListItem> pretraga(PredavanjeFilter f, Pageable p) {
        return service.pretraga(f, p);
    }

    private static Pageable strana() {
        return PageableUtil.proveri(PageRequest.of(0, 100), PredavanjeService.SORT_POLJA, PredavanjeService.PODRAZUMEVANI_SORT);
    }

    private static PredavanjeFilter filter(Long predmetId, Long grupaId, Integer godina, Boolean zavrseno, String q,
                                           LocalDate od, LocalDate doDatuma) {
        return new PredavanjeFilter(predmetId, grupaId, godina, zavrseno, q, od, doDatuma);
    }

    private static List<Long> ids(PagedModel<PredavanjeListItem> r) {
        return r.getContent().stream().map(PredavanjeListItem::getId).toList();
    }

    private List<Long> nasiIds(PagedModel<PredavanjeListItem> r) {
        return ids(r).stream().filter(nasi::contains).toList();
    }

    private static PredavanjeListItem nadji(List<PredavanjeListItem> r, Predavanje p) {
        return r.stream().filter(i -> i.getId().equals(p.getId())).findFirst().orElseThrow();
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

    private Student student(String indeks, Grupa g) {
        Student s = new Student();
        s.setIme("Ime");
        s.setPrezime(indeks);
        s.setIndeks(indeks);
        s.setGodina(g == null ? 2020 : g.getGodinaUpisa());
        s.setGrupa(g);
        em.persist(s);
        return s;
    }

    private Predavanje predavanje(Predmet predmet, Grupa g, int rb, LocalDate datum, String tema, Boolean zavrseno) {
        Predavanje p = new Predavanje();
        p.setPredmet(predmet);
        p.setGrupa(g);
        p.setRb(rb);
        p.setDatum(datum);
        p.setTema(tema);
        p.setZavrseno(zavrseno);
        em.persist(p);
        return p;
    }

    private void aktivnost(Predavanje p, Student s, TipAktivnosti tip) {
        em.persist(new Aktivnost(p, s, tip));
    }
}
