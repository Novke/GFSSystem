package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredmetRepository;

import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Broj vežbe, vrsta i izbor prezentacija nad pravom MySQL bazom (V19): kolona i popuna iz naziva, pravljenje, kopija,
 * {@code PATCH}, vrsta iz slajdova i izbor po grupi iz pravih izvođenja. Bez {@code @Transactional}: sve se commit-uje.
 */
@SpringBootTest
class PrezentacijaRedosledDbTest {

    @Autowired PrezentacijaService service;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired GrupaRepository grupaRepository;
    @Autowired JdbcTemplate jdbc;

    Predmet predmet;
    Grupa grupa;
    Grupa drugaGrupa;
    final List<Long> prezentacije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String oznaka = UUID.randomUUID().toString().substring(0, 8);
        predmet = new Predmet();
        predmet.setNaziv("Redosled DB test " + oznaka);
        predmet = predmetRepository.save(predmet);
        grupa = nova("RD-" + oznaka);
        drugaGrupa = nova("RD2-" + oznaka);
    }

    Grupa nova(String naziv) {
        Grupa g = new Grupa();
        g.setNaziv(naziv);
        g.setGodinaUpisa(2026);
        return grupaRepository.save(g);
    }

    @AfterEach
    void tearDown() {
        for (Long id : prezentacije) {
            jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", id);
            try {
                service.obrisi(id);
            } catch (SystemException e) {
                // već obrisana
            }
        }
        grupaRepository.deleteById(grupa.getId());
        grupaRepository.deleteById(drugaGrupa.getId());
        predmetRepository.deleteById(predmet.getId());
    }

    PrezentacijaDetails nova(String naziv, Integer vezba) {
        PrezentacijaDetails d = service.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), naziv, null, null, vezba));
        prezentacije.add(d.id());
        return d;
    }

    static SlajdCmd info() {
        return new SlajdCmd(TipSlajda.INFO, "Naslov", null, null, null, false, null, null, null);
    }

    static SlajdCmd pitanje() {
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, new PitanjeCmd(TipPitanja.KRATAK_TEKST,
                "Materijal?", null, null, null, null, null, null, null, null, List.of("beton"), null, null), null, null);
    }

    /** Završeno izvođenje grupe sa zadatim početkom. */
    void pusti(Long prezentacijaId, Grupa g, LocalDateTime pocetak) {
        IzvodjenjeInfo iz = izvodjenjeService.pokreni(prezentacijaId, new PokreniCmd(false, g.getId(), null));
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null, pocetak = ? where id = ?", pocetak,
                iz.id());
    }

    @Test
    void kolonaPostojiIPopunaIzNazivaJeIdempotentna() throws Exception {
        Long a = nova("Vežbe 3 i 4 – zadaci", null).id();
        Long b = nova("Vezbe 12 - ponavljanje", null).id();
        Long c = nova("Upoznavanje", null).id();
        Long d = nova("Vežbe 2", 7).id();
        String sql = new ClassPathResource("db/migration/V19__vezba_prezentacije.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        // V19 još jednom nad postojećom kolonom: bez greške, popunjava samo NULL redove (jedna veza: @promenljive)
        jdbc.execute((ConnectionCallback<Void>) veza -> {
            try (Statement st = veza.createStatement()) {
                for (String naredba : sql.replaceAll("(?m)^--.*$", "").split(";")) {
                    if (!naredba.isBlank()) {
                        st.execute(naredba);
                    }
                }
            }
            return null;
        });
        assertEquals(3, service.detalji(a).vezba());
        assertEquals(12, service.detalji(b).vezba());
        assertNull(service.detalji(c).vezba());
        assertEquals(7, service.detalji(d).vezba(), "postojeći broj se ne prepisuje");
    }

    @Test
    void pravljenjeKopijaIPatchBrojaVezbe() {
        PrezentacijaDetails p = nova("Vežbe 5", 5);
        assertEquals(5, p.vezba());
        assertEquals(VrstaPrezentacije.GRADIVO, p.vrsta());
        assertNotNull(p.kreirano());
        assertNull(p.poslednjePustanje());

        assertEquals(5, service.dupliraj(p.id()).vezba());
        prezentacije.add(service.lista(predmet.getId()).stream()
                .filter(x -> !prezentacije.contains(x.id())).findFirst().orElseThrow().id());

        LocalDateTime izmenjeno = service.detalji(p.id()).izmenjeno();
        assertEquals(6, service.postaviVezbu(p.id(), 6).vezba());
        assertEquals(izmenjeno, service.detalji(p.id()).izmenjeno(), "broj vežbe ne pomera datum izmene");
        assertNull(service.postaviVezbu(p.id(), null).vezba());
        assertNull(jdbc.queryForObject("select vezba from prezentacije where id = ?", Integer.class, p.id()));

        SystemException e = assertThrows(SystemException.class, () -> service.postaviVezbu(p.id(), 100));
        assertEquals("Broj vežbe mora biti od 0 do 99.", e.getMessage());
        assertThrows(SystemException.class, () -> nova("Loš broj", -1));
        assertEquals(404, assertThrows(SystemException.class, () -> service.postaviVezbu(-5L, 1)).getCode());
    }

    @Test
    void vrstaIzSlajdovaUListiIDetaljima() {
        Long gradivo = nova("Gradivo", 1).id();
        service.dodajSlajd(gradivo, info(), null);
        Long pitanja = nova("Pitanja", 0).id();
        service.dodajSlajd(pitanja, info(), null);
        service.dodajSlajd(pitanja, pitanje(), null);

        assertEquals(VrstaPrezentacije.GRADIVO, service.detalji(gradivo).vrsta());
        PrezentacijaDetails d = service.detalji(pitanja);
        assertEquals(VrstaPrezentacije.PITANJA, d.vrsta());
        assertEquals(0, d.brojZadataka());
        PrezentacijaInfo uListi = service.lista(predmet.getId()).stream().filter(x -> x.id().equals(pitanja))
                .findFirst().orElseThrow();
        assertEquals(VrstaPrezentacije.PITANJA, uListi.vrsta());
        assertEquals(0, uListi.vezba());
    }

    @Test
    void izborPratiGrupuIPoslednjePustanje() {
        Long upoznavanje = nova("Upoznavanje", 0).id();
        service.dodajSlajd(upoznavanje, pitanje(), null);
        Long v1 = nova("Vežbe 1", 1).id();
        Long v2 = nova("Vežbe 2", 2).id();
        Long v3 = nova("Vežbe 3", 3).id();
        Long bez = nova("Bez broja", null).id();

        LocalDateTime t1 = LocalDateTime.of(2026, 10, 14, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 21, 10, 0);
        pusti(upoznavanje, grupa, t1);
        pusti(v1, grupa, t1);
        pusti(v1, grupa, t2);
        pusti(v2, drugaGrupa, t2);

        IzborPrezentacija izbor = service.izbor(predmet.getId(), grupa.getId(), null);
        assertEquals(List.of(upoznavanje, v1, v2, v3, bez),
                izbor.stavke().stream().map(s -> s.prezentacija().id()).toList());
        assertEquals(t1, izbor.stavke().get(0).pustenoGrupi());
        assertEquals(t2, izbor.stavke().get(1).pustenoGrupi(), "najnovije puštanje grupe");
        assertNull(izbor.stavke().get(2).pustenoGrupi(), "druga grupa se ne računa");
        assertEquals(t2, izbor.stavke().get(2).prezentacija().poslednjePustanje(), "ali poslednje puštanje da");
        assertEquals(VrstaPrezentacije.PITANJA, izbor.stavke().get(0).prezentacija().vrsta());
        assertEquals(List.of(v2, v3), izbor.predlog());

        assertEquals(List.of(v3), service.izbor(predmet.getId(), drugaGrupa.getId(), null).predlog(),
                "druga grupa je stigla do vežbe 2, starije gradivo se ne nudi");
        assertEquals(List.of(v3), service.izbor(predmet.getId(), null, v2).predlog());
        assertEquals(List.of(), service.izbor(predmet.getId(), null, null).predlog());
        assertEquals(400, assertThrows(SystemException.class, () -> service.izbor(null, null, null))
                .getCode());
    }
}
