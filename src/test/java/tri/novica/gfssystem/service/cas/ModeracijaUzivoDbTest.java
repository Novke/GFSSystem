package tri.novica.gfssystem.service.cas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.entity.uzivo.Utisanje;
import tri.novica.gfssystem.entity.uzivo.VrstaOznake;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.*;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Moderacija uživo (spec 2026-11-18-moderacija-uzivo-design.md), nad pravom bazom: utišavanje, sakrivanje sa oznakom,
 * izbacivanje do kraja predavanja (student po oznaci, telefon po zabrani tokena), narandžast učesnik i karton.
 */
@SpringBootTest
class ModeracijaUzivoDbTest {

    @Autowired UcesnikService ucesnikService;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired StanjeService stanjeService;
    @Autowired OdgovorService odgovorService;
    @Autowired OznakePonasanja oznake;
    @Autowired CekiranjeService cekiranje;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper jsonMapper;
    @Autowired Clock clock;

    CasPodaci d;
    Long predavanje;
    Long ana;
    Long bora;
    IzvodjenjeInfo iz;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        Long grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now(clock));
        ana = d.student(grupa, "Ana");
        bora = d.student(grupa, "Bora");
        iz = pokreni(predavanje);
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
    }

    IzvodjenjeInfo pokreni(Long predavanjeId) {
        Long prez = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(d.predmetId, "Oblak reči", null, null)).id();
        prezentacijaService.dodajSlajd(prez, new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false,
                new PitanjeCmd(TipPitanja.KRATAK_TEKST, "Materijal?", null, null, null, null, null, null, null, null,
                        List.of("beton"), null, null), null, null), null);
        return izvodjenjeService.pokreni(prez, new PokreniCmd(false, null, predavanjeId));
    }

    /** Na pitanje i otvori rundu; vraća id runde. */
    Long otvori(IzvodjenjeInfo i) {
        izvodjenjeService.komanda(i.id(), new KomandaCmd(TipKomande.SLEDECI, null));
        izvodjenjeService.komanda(i.id(), new KomandaCmd(TipKomande.OTVORI_ZATVORI, null));
        return stanjeService.javno(i.id()).pitanje().rundaId();
    }

    void odgovori(IzvodjenjeInfo i, UcesnikService.Prijavljen u, Long runda, String tekst) {
        odgovorService.odgovori(i.id(), u.info().ucesnikId(), new OdgovorCmd(runda, null, null, tekst, null));
    }

    long oznakeStudenta(Long student, VrstaOznake v) {
        return jdbc.queryForObject("select count(*) from oznake_ponasanja where student_id = ? and vrsta = ?",
                Long.class, student, v.name());
    }

    UcesnikStanje uKonzoli(IzvodjenjeInfo i, UcesnikService.Prijavljen u) {
        return stanjeService.nastavnicko(i.id()).ucesnici().stream()
                .filter(x -> x.id().equals(u.info().ucesnikId())).findFirst().orElseThrow();
    }

    static void ocekuj(int status, String poruka, Runnable r) {
        SystemException e = assertThrows(SystemException.class, r::run);
        assertEquals(status, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    // ---------------------------------------------------------------- utišavanje

    @Test
    void utisanNeOdgovaraNaPitanjeDokSeNeSkine() {
        UcesnikService.Prijavljen a = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        Long runda = otvori(iz);

        izvodjenjeService.utisaj(iz.id(), a.info().ucesnikId(), Utisanje.PITALICE);
        assertEquals(1, oznakeStudenta(ana, VrstaOznake.UTISAN_PITALICE));
        assertEquals(Utisanje.PITALICE, stanjeService.licno(iz.id(), a.info().ucesnikId()).utisan());
        assertEquals(Utisanje.PITALICE, uKonzoli(iz, a).utisan());
        OdgovorOdbijen e = assertThrows(OdgovorOdbijen.class, () -> odgovori(iz, a, runda, "beton"));
        assertEquals(OdgovorService.UTISAN, e.getMessage());

        // ista vrednost: bez nove oznake; SVE: nova oznaka
        izvodjenjeService.utisaj(iz.id(), a.info().ucesnikId(), Utisanje.PITALICE);
        izvodjenjeService.utisaj(iz.id(), a.info().ucesnikId(), Utisanje.SVE);
        assertEquals(1, oznakeStudenta(ana, VrstaOznake.UTISAN_PITALICE));
        assertEquals(1, oznakeStudenta(ana, VrstaOznake.UTISAN_SVE));
        assertThrows(OdgovorOdbijen.class, () -> odgovori(iz, a, runda, "beton"));

        // skidanje: odgovara, oznake ostaju
        izvodjenjeService.utisaj(iz.id(), a.info().ucesnikId(), null);
        assertNull(stanjeService.licno(iz.id(), a.info().ucesnikId()).utisan());
        odgovori(iz, a, runda, "beton");
        assertEquals(2, oznakeStudenta(ana, VrstaOznake.UTISAN_PITALICE) + oznakeStudenta(ana, VrstaOznake.UTISAN_SVE));
    }

    @Test
    void utisanGostBezKartona() {
        UcesnikService.Prijavljen g = ucesnikService.prijavi(iz.kod(), "Gost", Optional.empty());
        izvodjenjeService.utisaj(iz.id(), g.info().ucesnikId(), Utisanje.SVE);
        assertEquals(0L, jdbc.queryForObject("select count(*) from oznake_ponasanja where izvodjenje_id = ?",
                Long.class, iz.id()));
        UcesnikStanje u = uKonzoli(iz, g);
        assertEquals(Utisanje.SVE, u.utisan());
        assertFalse(u.oznacen());
        assertEquals(0, u.brojOznaka());
        assertFalse(u.student());
    }

    @Test
    void javnoStanjeNemaModeraciju() throws Exception {
        UcesnikService.Prijavljen a = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        otvori(iz);
        izvodjenjeService.utisaj(iz.id(), a.info().ucesnikId(), Utisanje.SVE);
        String javno = jsonMapper.writeValueAsString(stanjeService.javno(iz.id()));
        assertFalse(javno.contains("utisan"), javno);
        assertFalse(javno.contains("oznak"), javno);
        String licno = jsonMapper.writeValueAsString(stanjeService.licno(iz.id(), a.info().ucesnikId()));
        assertFalse(licno.contains("oznak"), licno);
    }

    // ---------------------------------------------------------------- sakrivanje

    @Test
    void sakrijPiseOznakuIVracanjeJeBrise() {
        UcesnikService.Prijavljen a = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        UcesnikService.Prijavljen b = ucesnikService.prijavi(iz.kod(), "x", Optional.of(bora));
        UcesnikService.Prijavljen g = ucesnikService.prijavi(iz.kod(), "Gost", Optional.empty());
        Long runda = otvori(iz);
        odgovori(iz, a, runda, "Psovka");
        odgovori(iz, g, runda, "psovka ");
        odgovori(iz, b, runda, "beton");

        // iz panela učesnika: ključ je Anin tekst; sakriva i gostov isti tekst, oznaku dobija samo Ana
        izvodjenjeService.sakrij(iz.id(), runda, new SakrijCmd(null, true, a.info().ucesnikId()));
        assertEquals(1, oznakeStudenta(ana, VrstaOznake.SAKRIVEN_ODGOVOR));
        assertEquals("Psovka", jdbc.queryForObject(
                "select tekst from oznake_ponasanja where student_id = ? and vrsta = 'SAKRIVEN_ODGOVOR'", String.class, ana));
        assertEquals(0, oznakeStudenta(bora, VrstaOznake.SAKRIVEN_ODGOVOR));
        assertTrue(uKonzoli(iz, g).odgovorSakriven());
        UcesnikStanje ua = uKonzoli(iz, a);
        assertTrue(ua.oznacen());
        assertEquals(1, ua.brojOznaka());
        assertTrue(ua.student());
        assertFalse(uKonzoli(iz, b).oznacen());

        // ponovo sakrij: bez duple oznake
        izvodjenjeService.sakrij(iz.id(), runda, new SakrijCmd("psovka", true));
        assertEquals(1, oznakeStudenta(ana, VrstaOznake.SAKRIVEN_ODGOVOR));

        // vrati: oznaka nestaje
        izvodjenjeService.sakrij(iz.id(), runda, new SakrijCmd("psovka", false));
        assertEquals(0, oznakeStudenta(ana, VrstaOznake.SAKRIVEN_ODGOVOR));
        assertFalse(uKonzoli(iz, a).oznacen());
    }

    @Test
    void sakrijUcesnikaBezTekstualnogOdgovora404() {
        UcesnikService.Prijavljen a = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        Long runda = otvori(iz);
        ocekuj(404, "Učesnik nema tekstualni odgovor u ovoj rundi.",
                () -> izvodjenjeService.sakrij(iz.id(), runda, new SakrijCmd(null, true, a.info().ucesnikId())));
    }

    // ---------------------------------------------------------------- izbacivanje

    @Test
    void studentIzbacenDoKrajaPredavanjaNeUlaziUDrugoIzvodjenje() {
        UcesnikService.Prijavljen a = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        izvodjenjeService.izbaci(iz.id(), a.info().ucesnikId(), true);
        assertEquals(1, oznakeStudenta(ana, VrstaOznake.IZBACEN_PREDAVANJE));

        IzvodjenjeInfo drugo = pokreni(predavanje);
        ocekuj(403, UcesnikService.IZBACEN, () -> ucesnikService.prijavi(drugo.kod(), "x", Optional.of(ana)));
        // ni kao gost sa istog telefona
        ocekuj(403, UcesnikService.IZBACEN,
                () -> ucesnikService.prijavi(drugo.kod(), "Neko", Optional.empty(), a.token()));
        // izvođenje bez predavanja: slobodno
        IzvodjenjeInfo bez = pokreni(null);
        assertTrue(ucesnikService.prijavi(bez.kod(), "x", Optional.of(ana)).student());

        // brisanje oznake na kartonu skida zabranu za ostala izvođenja predavanja
        OznakaPonasanjaInfo o = oznake.zaStudenta(ana).getFirst();
        assertEquals(VrstaOznake.IZBACEN_PREDAVANJE, o.vrsta());
        assertEquals("Oblak reči", o.prezentacija());
        assertEquals(d.predmetId, o.predmet().id());
        oznake.obrisi(o.id());
        assertTrue(ucesnikService.prijavi(drugo.kod(), "x", Optional.of(ana)).student());
    }

    @Test
    void izbacenSamoIzOvogKasnijeDopunaDoKrajaPredavanja() {
        UcesnikService.Prijavljen a = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        izvodjenjeService.izbaci(iz.id(), a.info().ucesnikId(), false);
        izvodjenjeService.izbaci(iz.id(), a.info().ucesnikId(), false);
        assertEquals(1, oznakeStudenta(ana, VrstaOznake.IZBACEN));
        IzvodjenjeInfo drugo = pokreni(predavanje);
        assertTrue(ucesnikService.prijavi(drugo.kod(), "x", Optional.of(ana)).student());

        izvodjenjeService.izbaci(iz.id(), a.info().ucesnikId(), true);
        izvodjenjeService.izbaci(iz.id(), a.info().ucesnikId(), true);
        assertEquals(1, oznakeStudenta(ana, VrstaOznake.IZBACEN_PREDAVANJE));
    }

    @Test
    void izbaciDoKrajaPredavanjaBezPredavanja400() {
        IzvodjenjeInfo bez = pokreni(null);
        UcesnikService.Prijavljen g = ucesnikService.prijavi(bez.kod(), "Gost", Optional.empty());
        ocekuj(400, "Izvođenje nije pokrenuto sa predavanja.",
                () -> izvodjenjeService.izbaci(bez.id(), g.info().ucesnikId(), true));
    }

    @Test
    void izbacenGostNeUlaziSaIstogTelefona() {
        UcesnikService.Prijavljen g = ucesnikService.prijavi(iz.kod(), "Gost", Optional.empty());
        izvodjenjeService.izbaci(iz.id(), g.info().ucesnikId());
        ocekuj(403, UcesnikService.IZBACEN, () -> ucesnikService.prijavi(iz.kod(), "Drugo ime", Optional.empty(), g.token()));
        // drugi telefon (bez kolačića) ulazi, kao i do sada
        assertNotNull(ucesnikService.prijavi(iz.kod(), "Drugo ime", Optional.empty(), null).token());
        // samo iz ovog izvođenja: drugo izvođenje predavanja prima, a zabrana prelazi na nov token
        IzvodjenjeInfo drugo = pokreni(predavanje);
        UcesnikService.Prijavljen g2 = ucesnikService.prijavi(drugo.kod(), "Gost", Optional.empty(), g.token());
        ocekuj(403, UcesnikService.IZBACEN, () -> ucesnikService.prijavi(iz.kod(), "Treće", Optional.empty(), g2.token()));
    }

    @Test
    void zabranaGostaDoKrajaPredavanjaPrezivljavaBrisanjeUcesnika() {
        UcesnikService.Prijavljen g = ucesnikService.prijavi(iz.kod(), "Gost", Optional.empty());
        izvodjenjeService.izbaci(iz.id(), g.info().ucesnikId(), true);
        // "Ne čuvaj" na kraju briše učesnike; zabrana telefona ostaje
        jdbc.update("delete from ucesnici where izvodjenje_id = ?", iz.id());
        IzvodjenjeInfo drugo = pokreni(predavanje);
        ocekuj(403, UcesnikService.IZBACEN, () -> ucesnikService.prijavi(drugo.kod(), "Gost", Optional.empty(), g.token()));
    }

    // ---------------------------------------------------------------- narandžast i karton

    @Test
    void oznacenUDrugomIzvodjenjuIstogPredavanja() {
        UcesnikService.Prijavljen a = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        izvodjenjeService.utisaj(iz.id(), a.info().ucesnikId(), Utisanje.PITALICE);

        IzvodjenjeInfo drugo = pokreni(predavanje);
        UcesnikService.Prijavljen a2 = ucesnikService.prijavi(drugo.kod(), "x", Optional.of(ana));
        UcesnikService.Prijavljen b2 = ucesnikService.prijavi(drugo.kod(), "x", Optional.of(bora));
        UcesnikStanje ua = uKonzoli(drugo, a2);
        assertTrue(ua.oznacen());
        assertEquals(1, ua.brojOznaka());
        assertNull(ua.utisan(), "utišavanje važi do kraja izvođenja");
        assertFalse(uKonzoli(drugo, b2).oznacen());

        // izvođenje bez predavanja: oznaka sa predavanja se broji, ali ne boji
        IzvodjenjeInfo bez = pokreni(null);
        UcesnikStanje u3 = uKonzoli(bez, ucesnikService.prijavi(bez.kod(), "x", Optional.of(ana)));
        assertFalse(u3.oznacen());
        assertEquals(1, u3.brojOznaka());
    }

    @Test
    void kartonNepoznatStudentINepoznataOznaka404() {
        ocekuj(404, OznakePonasanja.STUDENT_NIJE_PRONADJEN, () -> oznake.zaStudenta(-1L));
        ocekuj(404, OznakePonasanja.OZNAKA_NIJE_PRONADJENA, () -> oznake.obrisi(-1L));
        assertEquals(List.of(), oznake.zaStudenta(bora));
    }

    @Test
    void brojOcekivanihBezIzbacenihDoKrajaPredavanja() {
        UcesnikService.Prijavljen a = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        izvodjenjeService.izbaci(iz.id(), a.info().ucesnikId(), true);
        cekiranje.skeniraj(cekiranje.zaNastavnika(predavanje).token(), Optional.of(ana), null);
        IzvodjenjeInfo drugo = pokreni(predavanje);
        assertEquals(0, stanjeService.nastavnicko(drugo.id()).brojOcekivanih());
        // telefon na času ne vodi Anu u izvođenje predavanja iz kog je izbačena
        assertTrue(cekiranje.aktivnoIzvodjenje(predavanje, ana).isEmpty());
    }
}
