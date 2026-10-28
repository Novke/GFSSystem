package tri.novica.gfssystem.service.cas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.NastavnickoStanje;
import tri.novica.gfssystem.dto.uzivo.PokreniCmd;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.service.uzivo.StanjeService;
import tri.novica.gfssystem.service.uzivo.UcesnikService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uživo uz čekiranje (spec studentskog dela 4.6, 4.7), nad pravom bazom: izbačen prijavljen student ne ulazi ponovo,
 * bodovano izvođenje sa predavanja traži čekiranje, a konzola broji očekivane (učesnici plus čekirani koji nisu ušli).
 * {@code boduje} se uključuje JDBC-om, kao u {@code UcesnikIdentitetDbTest}.
 */
@SpringBootTest
class CekiranjeUzivoDbTest {

    @Autowired UcesnikService ucesnikService;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired StanjeService stanjeService;
    @Autowired CekiranjeService cekiranje;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    CasPodaci d;
    Long grupa;
    Long predavanje;
    Long ana;
    Long bora;
    Long ceca;
    IzvodjenjeInfo iz;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now(clock));
        ana = d.student(grupa, "Ana");
        bora = d.student(grupa, "Bora");
        ceca = d.student(grupa, "Ceca");
        iz = pokreni(predavanje);
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
    }

    IzvodjenjeInfo pokreni(Long predavanjeId) {
        Long prez = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(d.predmetId, "Čas uživo", null, null)).id();
        return izvodjenjeService.pokreni(prez, new PokreniCmd(false, null, predavanjeId));
    }

    void cekiraj(Long student) {
        cekiranje.skeniraj(cekiranje.zaNastavnika(predavanje).token(), Optional.of(student), null);
    }

    void boduje(IzvodjenjeInfo i) {
        jdbc.update("update izvodjenja set boduje = 1 where id = ?", i.id());
    }

    long brojUcesnika(IzvodjenjeInfo i) {
        return jdbc.queryForObject("select count(*) from ucesnici where izvodjenje_id = ?", Long.class, i.id());
    }

    static void ocekuj403(String poruka, Runnable r) {
        SystemException e = assertThrows(SystemException.class, r::run);
        assertEquals(403, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    // ---------------------------------------------------------------- izbačen

    @Test
    void izbacenPrijavljenStudent403() {
        UcesnikService.Prijavljen u = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        izvodjenjeService.izbaci(iz.id(), u.info().ucesnikId());
        ocekuj403(UcesnikService.IZBACEN, () -> ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana)));
        assertEquals(1, brojUcesnika(iz));
    }

    @Test
    void izbacenKucanoImeRadiKaoDanas() {
        UcesnikService.Prijavljen u = ucesnikService.prijavi(iz.kod(), "Gost", Optional.empty());
        izvodjenjeService.izbaci(iz.id(), u.info().ucesnikId());
        assertNotNull(ucesnikService.prijavi(iz.kod(), "Gost", Optional.empty()).token());
        assertEquals(2, brojUcesnika(iz));
    }

    // ---------------------------------------------------------------- bodovano traži čekiranje

    @Test
    void bodovanoBezCekiranja403() {
        boduje(iz);
        ocekuj403(UcesnikService.PRVO_CEKIRANJE, () -> ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana)));
        assertEquals(0, brojUcesnika(iz));
    }

    @Test
    void bodovanoSaCekiranjemUlazi() {
        boduje(iz);
        cekiraj(ana);
        UcesnikService.Prijavljen p = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        assertTrue(p.student());
        // drugi telefon istog čekiranog studenta: isti red
        assertEquals(p.info().ucesnikId(), ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana)).info().ucesnikId());
    }

    @Test
    void bodovanoCekiranjeStarijeOd5h403() {
        boduje(iz);
        cekiraj(ana);
        ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        d.vremeCekiranja(predavanje, ana, LocalDateTime.now(clock).minusHours(5).minusMinutes(1));
        // i postojeći učesnik (drugi telefon) mora biti čekiran
        ocekuj403(UcesnikService.PRVO_CEKIRANJE, () -> ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana)));
    }

    @Test
    void bodovanoBezPrijavePrvoPotrebnaPrijava() {
        boduje(iz);
        ocekuj403(UcesnikService.POTREBNA_PRIJAVA, () -> ucesnikService.prijavi(iz.kod(), "x", Optional.empty()));
    }

    @Test
    void bodovanoIzbacenPreCekiranja() {
        boduje(iz);
        cekiraj(ana);
        UcesnikService.Prijavljen u = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        izvodjenjeService.izbaci(iz.id(), u.info().ucesnikId());
        ocekuj403(UcesnikService.IZBACEN, () -> ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana)));
    }

    @Test
    void nebodovanoBezCekiranjaUlazi() {
        assertTrue(ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana)).student());
    }

    @Test
    void bodovanoBezPredavanjaKaoDanas() {
        IzvodjenjeInfo bez = pokreni(null);
        boduje(bez);
        assertTrue(ucesnikService.prijavi(bez.kod(), "x", Optional.of(ana)).student());
    }

    // ---------------------------------------------------------------- brojOcekivanih

    @Test
    void brojOcekivanihUkljucujeCekiraneKojiNisuUsli() {
        cekiraj(ana);
        cekiraj(bora);
        cekiraj(ceca);
        ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        ucesnikService.prijavi(iz.kod(), "Gost", Optional.empty());
        NastavnickoStanje s = stanjeService.nastavnicko(iz.id());
        assertEquals(2, s.ucesnici().size());
        assertEquals(4, s.brojOcekivanih());
        assertEquals(3, s.brojCekiranih());
    }

    @Test
    void brojOcekivanihBezIzbacenih() {
        cekiraj(ana);
        cekiraj(bora);
        UcesnikService.Prijavljen u = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        izvodjenjeService.izbaci(iz.id(), u.info().ucesnikId());
        NastavnickoStanje s = stanjeService.nastavnicko(iz.id());
        assertEquals(0, s.ucesnici().size());
        assertEquals(1, s.brojOcekivanih(), "izbačen se ne broji, čekirana Bora da");
        assertEquals(2, s.brojCekiranih());
    }

    @Test
    void brojOcekivanihBezPredavanjaJeBrojUcesnika() {
        IzvodjenjeInfo bez = pokreni(null);
        cekiraj(ana);
        ucesnikService.prijavi(bez.kod(), "Gost", Optional.empty());
        NastavnickoStanje s = stanjeService.nastavnicko(bez.id());
        assertEquals(1, s.brojOcekivanih());
        assertNull(s.brojCekiranih());
    }

    @Test
    void brojOcekivanihBezStarihCekiranja() {
        cekiraj(ana);
        d.vremeCekiranja(predavanje, ana, LocalDateTime.now(clock).minusHours(6));
        NastavnickoStanje s = stanjeService.nastavnicko(iz.id());
        assertEquals(0, s.brojOcekivanih());
        assertEquals(0, s.brojCekiranih());
    }
}
