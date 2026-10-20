package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prijava učesnika sa identitetom studenta nad pravom MySQL bazom (kao CI), bez {@code @Transactional}: bodovan run bez
 * identiteta je 403, student ulazi pod imenom iz baze ({@code ucesnici.student_id}), isti student opet dobija isti red sa
 * novim tokenom (stari prestaje da važi), a nebodovan run bez identiteta radi kao pre. Grupa i studenti se prave JDBC-om,
 * {@code boduje} se uključuje JDBC-om (bez predavanja i prijave studenata u ovom buildu servis ga ne pušta).
 */
@SpringBootTest
class UcesnikIdentitetDbTest {

    static final String POTREBNA_PRIJAVA = "Potrebna je prijava studenta.";

    @Autowired UcesnikService ucesnikService;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JdbcTemplate jdbc;

    final String oznaka = UUID.randomUUID().toString().substring(0, 8);
    final List<Long> studenti = new ArrayList<>();
    Predmet predmet;
    Long grupaId;
    Long prezentacijaId;
    Long izvodjenjeId;
    String kod;
    Long s1;
    Long s2;

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Uživo identitet DB " + oznaka);
        predmet = predmetRepository.save(predmet);
        jdbc.update("insert into grupe (naziv, godina_upisa) values (?, 2025)", "ID-" + oznaka);
        grupaId = jdbc.queryForObject("select id from grupe where naziv = ?", Long.class, "ID-" + oznaka);
        s1 = student("Ana", "Petrović", "A1");
        s2 = student("Ana", "Petrović", "A2");

        PrezentacijaDetails p = prezentacijaService.kreiraj(
                new CreatePrezentacijaCmd(predmet.getId(), "Identitet uživo", null, null));
        prezentacijaId = p.id();
        IzvodjenjeInfo info = izvodjenjeService.pokreni(prezentacijaId, new PokreniCmd(false, null, null));
        izvodjenjeId = info.id();
        kod = info.kod();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", prezentacijaId);
        try {
            prezentacijaService.obrisi(prezentacijaId);   // izvođenja i učesnici kaskadom
        } catch (SystemException e) {
            // već obrisana
        }
        studenti.forEach(s -> jdbc.update("delete from studenti where id = ?", s));
        jdbc.update("delete from grupe where id = ?", grupaId);
        predmetRepository.deleteById(predmet.getId());
    }

    /** Student grupe sa jedinstvenim indeksom ({@code ime} i {@code prezime} smeju biti null). */
    Long student(String ime, String prezime, String indeks) {
        String pun = "ID-" + oznaka + "-" + indeks;
        jdbc.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values (?, ?, 2025, ?, ?)",
                ime, prezime, pun, grupaId);
        Long id = jdbc.queryForObject("select id from studenti where indeks = ?", Long.class, pun);
        studenti.add(id);
        return id;
    }

    void boduje() {
        jdbc.update("update izvodjenja set boduje = 1 where id = ?", izvodjenjeId);
    }

    Long studentUcesnika(Long ucesnikId) {
        return jdbc.queryForObject("select student_id from ucesnici where id = ?", Long.class, ucesnikId);
    }

    long brojUcesnika() {
        return jdbc.queryForObject("select count(*) from ucesnici where izvodjenje_id = ?", Long.class, izvodjenjeId);
    }

    @Test
    void bodovanBezIdentitetaJe403() {
        boduje();
        SystemException e = assertThrows(SystemException.class,
                () -> ucesnikService.prijavi(kod, "Ana", Optional.empty()));
        assertEquals(403, e.getCode());
        assertEquals(POTREBNA_PRIJAVA, e.getMessage());
        // stari oblik (bez identiteta) isto
        assertEquals(403, assertThrows(SystemException.class, () -> ucesnikService.prijavi(kod, "Ana")).getCode());
        assertEquals(0, brojUcesnika());
    }

    @Test
    void infoBodovanogRuna() {
        assertEquals(new JavnoIzvodjenjeInfo("Identitet uživo", false, false), ucesnikService.info(kod, false));
        boduje();
        assertEquals(new JavnoIzvodjenjeInfo("Identitet uživo", true, false), ucesnikService.info(kod, false));
        assertEquals(new JavnoIzvodjenjeInfo("Identitet uživo", true, true), ucesnikService.info(kod, true));
    }

    @Test
    void studentUlaziPodImenomIzBazeIstiStudentIstiRed() {
        boduje();
        UcesnikService.Prijavljen prvi = ucesnikService.prijavi(kod, "nadimak", Optional.of(s1));
        assertEquals("Ana Petrović", prvi.info().ime());
        assertTrue(prvi.student());
        assertEquals(s1, studentUcesnika(prvi.info().ucesnikId()));

        // isti student sa drugog telefona: isti red, nov token, stari više ne važi
        UcesnikService.Prijavljen drugi = ucesnikService.prijavi(kod, "drugo ime", Optional.of(s1));
        assertEquals(prvi.info().ucesnikId(), drugi.info().ucesnikId());
        assertEquals("Ana Petrović", drugi.info().ime());
        assertNotEquals(prvi.token(), drugi.token());
        assertTrue(ucesnikService.poTokenu(prvi.token()).isEmpty());
        Optional<Ucesnik> novi = ucesnikService.poTokenu(drugi.token());
        assertTrue(novi.isPresent());
        assertEquals(prvi.info().ucesnikId(), novi.get().getId());
        assertEquals(1, brojUcesnika());

        // drugi student istog imena dobija sufiks
        UcesnikService.Prijavljen ana2 = ucesnikService.prijavi(kod, "Ana", Optional.of(s2));
        assertNotEquals(prvi.info().ucesnikId(), ana2.info().ucesnikId());
        assertTrue(ana2.info().ime().endsWith(" 2"), ana2.info().ime());
        assertEquals(s2, studentUcesnika(ana2.info().ucesnikId()));
        assertEquals(2, brojUcesnika());
    }

    @Test
    void dugoIliPraznoImeStudentaNikadNeZakljucava() {
        boduje();
        // 45 znakova (dvostruko prezime): ulazi, ime je skraćeno na 40
        Long dug = student("Aleksandra-Marija", "Petrović-Šćepanović Vukotić", "D1");
        String ime = ucesnikService.prijavi(kod, "x", Optional.of(dug)).info().ime();
        assertTrue(ime.codePointCount(0, ime.length()) <= 40, ime);
        assertTrue(ime.startsWith("Aleksandra-Marija Petrović"), ime);

        // bez imena i prezimena: indeks kao ime
        Long bezImena = student(null, null, "P1");
        jdbc.update("update studenti set ime = '', prezime = '' where id = ?", bezImena);
        assertEquals("ID-" + oznaka + "-P1", ucesnikService.prijavi(kod, "", Optional.of(bezImena)).info().ime());
    }

    @Test
    void nebodovanBezIdentitetaKaoDanas() {
        UcesnikService.Prijavljen ana = ucesnikService.prijavi(kod, " Ana ", Optional.empty());
        assertEquals("Ana", ana.info().ime());
        assertFalse(ana.student());
        assertNull(studentUcesnika(ana.info().ucesnikId()));
        assertEquals("ana 2", ucesnikService.prijavi(kod, "ana").info().ime());
        SystemException e = assertThrows(SystemException.class, () -> ucesnikService.prijavi(kod, " ", Optional.empty()));
        assertEquals(400, e.getCode());

        // i u nebodovanom runu student sa identitetom ulazi kao on sam
        UcesnikService.Prijavljen student = ucesnikService.prijavi(kod, "nadimak", Optional.of(s1));
        assertEquals("Ana Petrović", student.info().ime());
        assertEquals(s1, studentUcesnika(student.info().ucesnikId()));
    }
}
