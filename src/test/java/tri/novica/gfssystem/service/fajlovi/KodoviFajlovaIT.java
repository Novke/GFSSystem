package tri.novica.gfssystem.service.fajlovi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.PokreniCmd;
import tri.novica.gfssystem.entity.fajlovi.KodFajlova;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Izdavanje i važenje kratkih kodova fajlova (spec fajlova, odeljak 4) nad pravom bazom, sa pomerljivim satom. */
@SpringBootTest
@Import(PomerljivSatConfig.class)
class KodoviFajlovaIT {

    @Autowired KodoviFajlova kodovi;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired IzvodjenjeRepository izvodjenjeRepository;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired MedijService medijService;
    @Autowired PomerljivSatConfig.Sat sat;
    @Autowired JdbcTemplate jdbc;

    CasPodaci d;
    FajloviPodaci f;
    Long grupa;
    Long predavanje;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        f = new FajloviPodaci(jdbc, medijService, prezentacijaService, d.predmetId);
        grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now());
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
        f.ocisti();
    }

    Izvodjenje pokreni(Long predavanjeId) {
        IzvodjenjeInfo info = izvodjenjeService.pokreni(f.prezentacija("P " + System.nanoTime()),
                new PokreniCmd(false, null, predavanjeId));
        return izvodjenjeRepository.findSaVezama(info.id()).orElseThrow();
    }

    KodFajlova kod(Izvodjenje iz) {
        return kodovi.obezbedi(iz).orElseThrow();
    }

    @Test
    void izdajeKodOdCetiriCifre() {
        KodFajlova k = kod(pokreni(predavanje));
        assertTrue(k.getKod().matches("[1-9][0-9]{3}"), k.getKod());
        assertEquals(k.getKod(), k.getAktivanKod());
        assertEquals(predavanje, k.getPredavanje().getId());
        assertTrue(kodovi.vazeci(k.getKod()).isPresent());
    }

    @Test
    void drugoIzvodjenjeIstogPredavanjaDobijaIstiKod() {
        KodFajlova prvi = kod(pokreni(predavanje));
        KodFajlova drugi = kod(pokreni(predavanje));
        assertEquals(prvi.getId(), drugi.getId());
        assertEquals(prvi.getKod(), drugi.getKod());
        assertEquals(1, jdbc.queryForObject("select count(*) from kodovi_fajlova where predavanje_id = ?", Integer.class, predavanje));
    }

    @Test
    void posle5hKodIstekneANovoIzvodjenjeDobijaNoviBrojUIstomRedu() {
        KodFajlova prvi = kod(pokreni(predavanje));
        sat.pomeri(Duration.ofHours(5).plusMinutes(1));
        assertTrue(kodovi.vazeci(prvi.getKod()).isEmpty());

        KodFajlova novi = kod(pokreni(predavanje));
        assertEquals(prvi.getId(), novi.getId());   // jedan red po predavanju
        assertTrue(novi.getVaziDo().isAfter(prvi.getVaziDo()));
        assertTrue(kodovi.vazeci(novi.getKod()).isPresent());
    }

    @Test
    void zavrsenoPredavanjeKodNeVazi() {
        KodFajlova k = kod(pokreni(predavanje));
        d.zavrsi(predavanje);
        assertTrue(kodovi.vazeci(k.getKod()).isEmpty());
    }

    @Test
    void izvodjenjeBezPredavanjaVaziDokJeAktivno() {
        Izvodjenje iz = pokreni(null);
        KodFajlova k = kod(iz);
        assertNull(k.getPredavanje());
        assertEquals(iz.getId(), k.getIzvodjenje().getId());
        assertTrue(kodovi.vazeci(k.getKod()).isPresent());

        f.zavrsiIzvodjenje(iz.getId());
        assertTrue(kodovi.vazeci(k.getKod()).isEmpty());
    }

    @Test
    void izvodjenjeBezPredavanjaVaziNajduze12h() {
        KodFajlova k = kod(pokreni(null));
        sat.pomeri(Duration.ofHours(11));
        assertTrue(kodovi.vazeci(k.getKod()).isPresent());
        sat.pomeri(Duration.ofHours(2));
        assertTrue(kodovi.vazeci(k.getKod()).isEmpty());
    }

    @Test
    void istekliBrojSeOslobadjaPreIzboraNovog() {
        KodFajlova stari = kod(pokreni(null));
        Long staroIzvodjenje = stari.getIzvodjenje().getId();
        f.zavrsiIzvodjenje(staroIzvodjenje);

        kod(pokreni(predavanje));   // obezbedi() prvo oslobodi nevažeće
        assertNull(jdbc.queryForObject("select aktivan_kod from kodovi_fajlova where id = ?", String.class, stari.getId()));
        assertEquals(stari.getKod(), jdbc.queryForObject("select kod from kodovi_fajlova where id = ?", String.class, stari.getId()));
    }

    @Test
    void kodSeNeBrisePriZavrsetkuIzvodjenja() {
        KodFajlova k = kod(pokreni(null));
        f.zavrsiIzvodjenje(k.getIzvodjenje().getId());
        assertEquals(1, jdbc.queryForObject("select count(*) from kodovi_fajlova where id = ?", Integer.class, k.getId()));
    }

    @Test
    void losFormatINepoznatKod() {
        KodFajlova k = kod(pokreni(predavanje));
        for (String s : new String[]{null, "", "12", "12345", "abcd", " 123", k.getKod() + "0"}) {
            assertEquals(Optional.empty(), kodovi.vazeci(s), String.valueOf(s));
        }
        String drugi = k.getKod().equals("1000") ? "1001" : "1000";
        assertTrue(kodovi.vazeci(drugi).isEmpty());
    }

    @Test
    void zavrsenoPredavanjeNeDobijaKod() {
        d.zavrsi(predavanje);
        // pokreni na završenom predavanju je dozvoljeno, ali kod se ne izdaje
        Izvodjenje iz = pokreni(predavanje);
        assertTrue(kodovi.obezbedi(iz).isEmpty());
    }
}
