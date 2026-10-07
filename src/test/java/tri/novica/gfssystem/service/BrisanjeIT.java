package tri.novica.gfssystem.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.entity.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Brisanje predavanja, domaćeg i testa nad pravom MySQL bazom (Flyway šema, {@code SPRING_DATASOURCE_URL}); svaki test
 * se vraća unazad. Provere broje samo redove koje test sam ubaci.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BrisanjeIT {

    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    Predmet predmet;
    Grupa grupa;
    Student s1, s2;
    Predavanje predavanje, drugoPredavanje;
    Domaci vezan, nevezan;
    tri.novica.gfssystem.entity.Test test;

    @BeforeEach
    void seed() {
        predmet = new Predmet();
        predmet.setNaziv("Brisanje IT");
        em.persist(predmet);
        grupa = new Grupa();
        grupa.setNaziv("BR-A");
        grupa.setGodinaUpisa(2025);
        em.persist(grupa);
        s1 = student("BR1");
        s2 = student("BR2");

        predavanje = predavanje(92001);
        drugoPredavanje = predavanje(92002);
        em.persist(new Aktivnost(predavanje, s1, TipAktivnosti.PRISUSTVO));
        em.persist(new Aktivnost(predavanje, s2, TipAktivnosti.ZADATAK));
        em.persist(new Aktivnost(drugoPredavanje, s1, TipAktivnosti.PRISUSTVO));

        vezan = domaci("Vezan", predavanje);
        nevezan = domaci("Nevezan", drugoPredavanje);
        uradjen(vezan, s1);
        uradjen(vezan, s2);
        uradjen(nevezan, s1);

        TipTesta tip = new TipTesta("Kolokvijum BR", predmet);
        em.persist(tip);
        test = new tri.novica.gfssystem.entity.Test();
        test.setTipTesta(tip);
        test.setPredmet(predmet);
        test.setGrupa(grupa);
        test.setDatum(LocalDate.of(2025, 11, 1));
        test.setMaxPoena(100);
        test.setPregledan(false);
        test.setGrupe(new HashSet<>(Set.of(TestGrupa.A)));
        em.persist(test);
        polaganje(test, s1);
        polaganje(test, s2);

        em.flush();
        em.clear();
    }

    // ------------------------------------------------------------------ predavanje

    @Test
    void brisanjePredavanjaBriseAktivnostiAOdvezujeDomace() throws Exception {
        mvc.perform(delete("/predavanja/{id}", predavanje.getId())).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertNull(em.find(Predavanje.class, predavanje.getId()));
        assertEquals(0, broj("select count(a) from Aktivnost a where a.predavanje.id = " + predavanje.getId()));
        // domaći ostaje, bez predavanja, sa urađenim domaćim
        Domaci d = em.find(Domaci.class, vezan.getId());
        assertNotNull(d);
        assertNull(d.getPredavanje());
        assertEquals(2, broj("select count(u) from UradjenDomaci u where u.domaci.id = " + vezan.getId()));
    }

    @Test
    void brisanjePredavanjaNeDiraDrugaPredavanja() throws Exception {
        mvc.perform(delete("/predavanja/{id}", predavanje.getId())).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertNotNull(em.find(Predavanje.class, drugoPredavanje.getId()));
        assertEquals(1, broj("select count(a) from Aktivnost a where a.predavanje.id = " + drugoPredavanje.getId()));
        assertEquals(drugoPredavanje.getId(), em.find(Domaci.class, nevezan.getId()).getPredavanje().getId());
    }

    @Test
    void brisanjeNepostojecegPredavanjaJe404() throws Exception {
        mvc.perform(delete("/predavanja/{id}", 999999999L)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("Predavanje ne postoji! ID = 999999999"));
    }

    // ------------------------------------------------------------------ domaći

    @Test
    void brisanjeDomacegBriseUradjeneDomace() throws Exception {
        mvc.perform(delete("/domaci/{id}", vezan.getId())).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertNull(em.find(Domaci.class, vezan.getId()));
        assertEquals(0, broj("select count(u) from UradjenDomaci u where u.domaci.id = " + vezan.getId()));
        // predavanje i tuđi domaći ostaju
        assertNotNull(em.find(Predavanje.class, predavanje.getId()));
        assertNotNull(em.find(Domaci.class, nevezan.getId()));
        assertEquals(1, broj("select count(u) from UradjenDomaci u where u.domaci.id = " + nevezan.getId()));
    }

    @Test
    void brisanjeNepostojecegDomacegJe404() throws Exception {
        mvc.perform(delete("/domaci/{id}", 999999999L)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("Domaci ne postoji! ID = 999999999"));
    }

    // ------------------------------------------------------------------ test

    @Test
    void brisanjeTestaBrisePolaganja() throws Exception {
        mvc.perform(delete("/test/{id}", test.getId())).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertNull(em.find(tri.novica.gfssystem.entity.Test.class, test.getId()));
        assertEquals(0, broj("select count(p) from Polaganje p where p.test.id = " + test.getId()));
        // studenti i tip testa ostaju
        assertNotNull(em.find(Student.class, s1.getId()));
        assertNotNull(em.find(TipTesta.class, test.getTipTesta().getId()));
    }

    @Test
    void brisanjeNepostojecegTestaJe404() throws Exception {
        mvc.perform(delete("/test/{id}", 999999999L)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("Test ne postoji! ID = 999999999"));
    }

    // ------------------------------------------------------------------ pomoćne

    private long broj(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    private Student student(String indeks) {
        Student s = new Student();
        s.setIme("Ime");
        s.setPrezime(indeks);
        s.setIndeks(indeks);
        s.setGodina(grupa.getGodinaUpisa());
        s.setGrupa(grupa);
        em.persist(s);
        return s;
    }

    private Predavanje predavanje(int rb) {
        Predavanje p = new Predavanje();
        p.setPredmet(predmet);
        p.setGrupa(grupa);
        p.setRb(rb);
        p.setDatum(LocalDate.of(2025, 10, 1));
        p.setTema("Tema " + rb);
        em.persist(p);
        return p;
    }

    private Domaci domaci(String naslov, Predavanje p) {
        Domaci d = new Domaci();
        d.setPredmet(predmet);
        d.setGrupa(grupa);
        d.setPredavanje(p);
        d.setNaslov(naslov);
        d.setDatum(LocalDate.of(2025, 10, 2));
        d.setPregledan(false);
        em.persist(d);
        return d;
    }

    private void uradjen(Domaci d, Student s) {
        UradjenDomaci u = new UradjenDomaci();
        u.setDomaci(d);
        u.setStudent(s);
        u.setBodovi(5);
        em.persist(u);
    }

    private void polaganje(tri.novica.gfssystem.entity.Test t, Student s) {
        Polaganje p = Polaganje.defaultPolaganje(t, s);
        p.setGrupa(TestGrupa.A);
        em.persist(p);
    }
}
