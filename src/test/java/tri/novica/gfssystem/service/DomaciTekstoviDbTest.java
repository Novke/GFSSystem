package tri.novica.gfssystem.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.domaci.DomaciTekst;
import tri.novica.gfssystem.entity.Domaci;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tekstovi domaćih predmeta kao izvor za banku ({@code domaci_tekstovi}) nad pravom bazom: najnoviji prvi (datum, pa
 * id), samo zadati predmet, samo id, naslov, tekst i datum (nikad grupa, studenti ni predaje). Test se vraća unazad.
 */
@SpringBootTest
@Transactional
class DomaciTekstoviDbTest {

    @Autowired DomaciService service;
    @Autowired EntityManager em;

    Predmet predmet;
    Predmet drugi;
    Grupa grupa;
    Domaci stari, srednji, novi;

    @BeforeEach
    void seed() {
        predmet = predmet("Domaći tekstovi");
        drugi = predmet("Domaći tekstovi (drugi)");
        grupa = new Grupa();
        grupa.setNaziv("DT-A");
        grupa.setGodinaUpisa(2026);
        em.persist(grupa);

        stari = domaci(predmet, grupa, "Prvi domaći", "Saberi 2 i 3.", LocalDate.of(2026, 10, 1));
        srednji = domaci(predmet, null, "Drugi domaći", "Pretvori 12 u binarni.", LocalDate.of(2026, 10, 8));
        novi = domaci(predmet, grupa, "Treći domaći", "Izračunaj $x^2$ za x = 3.", LocalDate.of(2026, 10, 8));
        domaci(drugi, grupa, "Tuđi domaći", "Ne sme se videti.", LocalDate.of(2026, 10, 15));
        em.flush();
        em.clear();
    }

    Predmet predmet(String naziv) {
        Predmet p = new Predmet();
        p.setNaziv(naziv);
        em.persist(p);
        return p;
    }

    Domaci domaci(Predmet p, Grupa g, String naslov, String text, LocalDate datum) {
        Domaci d = new Domaci();
        d.setPredmet(p);
        d.setGrupa(g);
        d.setNaslov(naslov);
        d.setText(text);
        d.setDatum(datum);
        d.setPregledan(false);
        em.persist(d);
        return d;
    }

    @Test
    void prvaStranaDajeDvaNajnovijaSaTacnimPoljima() {
        Page<DomaciTekst> strana = service.tekstoviPredmeta(predmet.getId(), PageRequest.of(0, 2));
        assertEquals(3, strana.getTotalElements());
        assertEquals(2, strana.getTotalPages());
        // isti datum: veći id prvi
        assertEquals(List.of(
                new DomaciTekst(novi.getId(), "Treći domaći", "Izračunaj $x^2$ za x = 3.", LocalDate.of(2026, 10, 8)),
                new DomaciTekst(srednji.getId(), "Drugi domaći", "Pretvori 12 u binarni.", LocalDate.of(2026, 10, 8))),
                strana.getContent());

        Page<DomaciTekst> druga = service.tekstoviPredmeta(predmet.getId(), PageRequest.of(1, 2));
        assertEquals(List.of(new DomaciTekst(stari.getId(), "Prvi domaći", "Saberi 2 i 3.", LocalDate.of(2026, 10, 1))),
                druga.getContent());
    }

    @Test
    void bezGrupeStudenataIPredaja() {
        assertEquals(List.of("id", "naslov", "text", "datum"), Arrays.stream(DomaciTekst.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void predmetBezDomacihJePrazan() {
        Predmet prazan = predmet("Bez domaćih");
        em.flush();
        Page<DomaciTekst> strana = service.tekstoviPredmeta(prazan.getId(), PageRequest.of(0, 20));
        assertTrue(strana.isEmpty());
        assertEquals(0, strana.getTotalElements());
    }
}
