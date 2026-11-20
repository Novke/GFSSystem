package tri.novica.gfssystem.service.fajlovi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeCmd;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeInfo;
import tri.novica.gfssystem.dto.fajlovi.FajloviSlajda;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.KomandaCmd;
import tri.novica.gfssystem.dto.uzivo.NastavnickoStanje;
import tri.novica.gfssystem.dto.uzivo.PokreniCmd;
import tri.novica.gfssystem.dto.uzivo.TipKomande;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.service.uzivo.StanjeService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fajlovi uz prezentaciju nad pravom bazom: CRUD i granice (30, tuđ slajd, redosled), brisanje prezentacije i slajda,
 * upis pregleda slajda, kod pri pokretanju i fajlovi u stanju (samo za slajd sa fajlom).
 */
@SpringBootTest
@Import(PomerljivSatConfig.class)
class FajloviPrezentacijeIT {

    @Autowired FajloviPrezentacijeServis servis;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired StanjeService stanjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired MedijService medijService;
    @Autowired JdbcTemplate jdbc;

    CasPodaci d;
    FajloviPodaci f;
    Long prez;
    Long s1;
    Long s2;
    final List<String> mediji = new ArrayList<>();

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        f = new FajloviPodaci(jdbc, medijService, prezentacijaService, d.predmetId);
        prez = f.prezentacija("Fajlovi " + System.nanoTime());
        s1 = f.slajd(prez, "Prvi");
        s2 = f.slajd(prez, "Drugi");
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
        mediji.forEach(m -> {
            try { medijService.obrisi(m); } catch (RuntimeException ignore) { /* već obrisan */ }
        });
        f.ocisti();
    }

    FajlPrezentacijeInfo otpremi(Long p, String naziv, Long slajd) {
        FajlPrezentacijeInfo i = servis.dodaj(p, new MockMultipartFile("fajl", naziv, "application/octet-stream", FajloviPodaci.PK), slajd);
        mediji.add(jdbc.queryForObject("select medij_id from fajlovi_prezentacije where id = ?", String.class, i.id()));
        return i;
    }

    static int status(Runnable r) {
        try {
            r.run();
        } catch (SystemException e) {
            return e.getCode();
        }
        return 0;
    }

    @Test
    void dodajeIzmeniRedosledIObrisi() {
        FajlPrezentacijeInfo a = otpremi(prez, "a.docx", null);
        FajlPrezentacijeInfo b = otpremi(prez, "b.docx", s1);
        assertEquals(List.of(a.id(), b.id()), servis.lista(prez).stream().map(FajlPrezentacijeInfo::id).toList());
        assertEquals("DOCX", a.vrsta());
        assertNull(a.slajdId());
        assertEquals(s1, b.slajdId());

        FajlPrezentacijeInfo izm = servis.izmeni(a.id(), new FajlPrezentacijeCmd("novo", s2));
        assertEquals("novo.docx", izm.naziv());
        assertEquals(s2, izm.slajdId());

        servis.redosled(prez, List.of(b.id(), a.id()));
        assertEquals(List.of(b.id(), a.id()), servis.lista(prez).stream().map(FajlPrezentacijeInfo::id).toList());

        servis.obrisi(a.id());
        assertEquals(1, servis.lista(prez).size());
    }

    @Test
    void redosledTrazisveIdIJednomSvaki() {
        FajlPrezentacijeInfo a = otpremi(prez, "a.docx", null);
        FajlPrezentacijeInfo b = otpremi(prez, "b.docx", null);
        assertEquals(400, status(() -> servis.redosled(prez, List.of(a.id()))));
        assertEquals(400, status(() -> servis.redosled(prez, List.of(a.id(), a.id()))));
        assertEquals(400, status(() -> servis.redosled(prez, List.of(a.id(), b.id(), 999_999_999L))));
    }

    @Test
    void slajdMoraBitiIzIstePrezentacije() {
        Long druga = f.prezentacija("Druga " + System.nanoTime());
        Long tudj = f.slajd(druga, "Tuđ");
        MockMultipartFile mf = new MockMultipartFile("fajl", "a.docx", "application/octet-stream", FajloviPodaci.PK);
        assertEquals(400, status(() -> servis.dodaj(prez, mf, tudj)));
        FajlPrezentacijeInfo a = otpremi(prez, "a.docx", null);
        assertEquals(400, status(() -> servis.izmeni(a.id(), new FajlPrezentacijeCmd("a", tudj))));
    }

    @Test
    void najviseTridesetFajlovaPoPrezentaciji() {
        for (int i = 0; i < FajloviPrezentacijeServis.NAJVISE; i++) {
            f.fajl(prez, null, "f" + i + ".docx");
        }
        MockMultipartFile mf = new MockMultipartFile("fajl", "visak.docx", "application/octet-stream", FajloviPodaci.PK);
        assertEquals(FajloviPrezentacijeServis.PREVISE_FAJLOVA,
                assertThrows(SystemException.class, () -> servis.dodaj(prez, mf, null)).getMessage());
    }

    @Test
    void dodajIzMedijaTraziNevezanFajl() {
        String medij = medijService.sacuvajFajl(FajloviPodaci.PK, "m.docx").id();
        mediji.add(medij);
        FajlPrezentacijeInfo i = servis.dodajIzMedija(prez, medij, s1, "Naziv", "serija-1");
        assertEquals("Naziv.docx", i.naziv());
        assertEquals(409, status(() -> servis.dodajIzMedija(prez, medij, null, null, "serija-1")));
        String slika = medijService.sacuvaj(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2}, "s.png").id();
        mediji.add(slika);
        assertEquals(409, status(() -> servis.dodajIzMedija(prez, slika, null, null, "serija-1")));
    }

    @Test
    void brisanjePrezentacijeBriseFajloveIMedije() {
        FajlPrezentacijeInfo a = otpremi(prez, "a.docx", s1);
        String medij = jdbc.queryForObject("select medij_id from fajlovi_prezentacije where id = ?", String.class, a.id());
        prezentacijaService.obrisi(prez);
        assertEquals(0, jdbc.queryForObject("select count(*) from fajlovi_prezentacije where prezentacija_id = ?", Integer.class, prez));
        assertEquals(0, jdbc.queryForObject("select count(*) from mediji where id = ?", Integer.class, medij));
    }

    @Test
    void brisanjeSlajdaOstavljaFajlUzCeluPrezentaciju() {
        FajlPrezentacijeInfo a = otpremi(prez, "a.docx", s2);
        prezentacijaService.obrisiSlajd(s2);
        FajlPrezentacijeInfo posle = servis.lista(prez).stream().filter(x -> x.id().equals(a.id())).findFirst().orElseThrow();
        assertNull(posle.slajdId());
    }

    @Test
    void pokretanjeDajeKodAPrikazSlajdaSePise() {
        Long grupa = d.grupa(2026);
        Long predavanje = d.predavanje(grupa, LocalDate.now());
        otpremi(prez, "uz-prvi.docx", s1);
        otpremi(prez, "uz-sve.zip", null);

        IzvodjenjeInfo prvo = izvodjenjeService.pokreni(prez, new PokreniCmd(false, null, predavanje));
        String kod = jdbc.queryForObject("select aktivan_kod from kodovi_fajlova where predavanje_id = ?", String.class, predavanje);
        assertNotNull(kod);
        izvodjenjeService.komanda(prvo.id(), new KomandaCmd(TipKomande.ZAVRSI, null));
        IzvodjenjeInfo drugo = izvodjenjeService.pokreni(prez, new PokreniCmd(false, null, predavanje));
        assertEquals(kod, jdbc.queryForObject("select aktivan_kod from kodovi_fajlova where predavanje_id = ?", String.class, predavanje));

        // prijava (pre prvog slajda): bez fajlova
        assertNull(stanjeService.nastavnicko(drugo.id()).fajlovi());
        assertTrue(stanjeService.javno(drugo.id()).fajlovi().isEmpty());

        NastavnickoStanje na1 = izvodjenjeService.komanda(drugo.id(), new KomandaCmd(TipKomande.IDI_NA, 0));
        FajloviSlajda fs = na1.fajlovi();
        assertNotNull(fs);
        assertEquals(kod, fs.kod());
        assertTrue(fs.link().endsWith("/f/" + kod), fs.link());
        assertEquals(List.of("uz-prvi.docx", "uz-sve.zip"), fs.fajlovi().stream().map(x -> x.naziv()).toList());
        assertEquals(2, stanjeService.javno(drugo.id()).fajlovi().size());
        assertEquals(1, jdbc.queryForObject("select count(*) from slajdovi_izvodjenja where izvodjenje_id = ? and slajd_id = ?",
                Integer.class, drugo.id(), s1));

        // drugi slajd: samo fajl uz celu prezentaciju
        NastavnickoStanje na2 = izvodjenjeService.komanda(drugo.id(), new KomandaCmd(TipKomande.IDI_NA, 1));
        assertEquals(List.of("uz-sve.zip"), na2.fajlovi().fajlovi().stream().map(x -> x.naziv()).toList());
    }

    @Test
    void stanjeBezFajlovaNemaPolje() {
        IzvodjenjeInfo iz = izvodjenjeService.pokreni(prez, new PokreniCmd(false, null, null));
        NastavnickoStanje n = izvodjenjeService.komanda(iz.id(), new KomandaCmd(TipKomande.IDI_NA, 0));
        assertNull(n.fajlovi());
        assertTrue(stanjeService.javno(iz.id()).fajlovi().isEmpty());
        otpremi(prez, "kasnije.docx", s1);   // prezentacija koja se izvodi sme da se menja fajlovima
        assertEquals(1, stanjeService.nastavnicko(iz.id()).fajlovi().fajlovi().size());
    }
}
