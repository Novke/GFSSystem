package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.repository.uzivo.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * "Ni ranije" za Zadatak slajd (spec 11.7) nad mokovima: telefon nikad ne dobija tuđu varijantu, rešenje pre RESENJE
 * ni tačne vrednosti pre TACAN; projektor ({@code javniRezultat}) po tri nezavisna prekidača. Snimak: A (svg-A, res-A,
 * a=4545) i B (svg-B, res-B, a=1313); učesnik 1 ima A i odgovorio je tačno ("4545"), učesnik 2 ima B i odgovorio je
 * netačno ("1"). Tajne se traže u serijalizovanom JSON-u (ono što zaista ide na mrežu).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZadatakStanjeTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 21, 10, 0);
    static final Long IZ = 5L;
    static final Long SLAJD = 12L;
    static final Long RUNDA = 202L;
    static final Long VAR_A = 101L;
    static final Long VAR_B = 102L;

    @Mock ZadaciSlajdova zadaci;
    @Mock ZadatakDodelaRepository dodelaRepository;
    @Mock IzvodjenjeRepository izvodjenjeRepository;
    @Mock SlajdRepository slajdRepository;
    @Mock PitanjeRundaRepository rundaRepository;
    @Mock OdgovorRepository odgovorRepository;
    @Mock UcesnikRepository ucesnikRepository;

    final MutableClock clock = new MutableClock(T0.plusSeconds(5).atZone(ZONA).toInstant(), ZONA);
    final JsonMapper jsonMapper = JsonMapper.builder().build();
    StanjeService service;

    Izvodjenje iz;
    Slajd slajd;
    final List<PitanjeRunda> runde = new ArrayList<>();
    final List<Ucesnik> ucesnici = new ArrayList<>();
    final List<Odgovor> odgovori = new ArrayList<>();
    Ucesnik u1, u2;

    static final List<Polje> POLJA = List.of(new Polje("a", "a)", TipPolja.BROJ, 0.0, OdstupanjeTip.APSOLUTNO, null,
            null, null, null));

    @BeforeEach
    void setUp() {
        Predmet predmet = new Predmet();
        predmet.setId(7L);
        Prezentacija prez = new Prezentacija();
        prez.setId(1L);
        prez.setNaziv("Vežbe 1");
        prez.setPredmet(predmet);

        slajd = new Slajd();
        slajd.setId(SLAJD);
        slajd.setRb(1);
        slajd.setTip(TipSlajda.ZADATAK);
        slajd.setZadatakBrojVarijanti(4);

        iz = new Izvodjenje();
        iz.setId(IZ);
        iz.setPrezentacija(prez);
        iz.setKod("123456");
        iz.setAktivanKod("123456");
        iz.setStatus(StatusIzvodjenja.AKTIVNO);
        iz.setPocetak(T0);
        iz.setPrikaz(Prikaz.SLAJD);
        iz.setTrenutniSlajdId(SLAJD);
        iz.setEkran(Ekran.NORMALAN);
        iz.setTelefonPrikaz(TelefonPrikaz.DUGMAD);
        iz.setVerzija(3);
        iz.setRezultatiPrikazani(true);

        u1 = ucesnik(1L, "Ana");
        u2 = ucesnik(2L, "Bojan");

        ZadatakSlajdaInfo info = new ZadatakSlajdaInfo(3L, null, 4, null, "2 → 10", List.of(new GrupaPutanja(3L, "2 → 10")),
                true, 6, List.of(0, 6, 0), POLJA, false, List.of(), List.of(), "primer-svg");
        when(zadaci.za(eq(7L), anyList())).thenReturn(Map.of(SLAJD, info));
        when(izvodjenjeRepository.findSaVezama(IZ)).thenReturn(Optional.of(iz));
        when(slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(1L)).thenReturn(List.of(slajd));
        when(rundaRepository.findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(IZ)).thenReturn(runde);
        when(ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(IZ)).thenReturn(ucesnici);
        when(odgovorRepository.findAllByRundaIzvodjenjeId(IZ)).thenReturn(odgovori);
        when(dodelaRepository.parovi(RUNDA)).thenReturn(List.of(new Object[]{1L, VAR_A}, new Object[]{2L, VAR_B}));

        service = new StanjeService(izvodjenjeRepository, slajdRepository, rundaRepository, odgovorRepository,
                ucesnikRepository, dodelaRepository, new PovezanostRegistar(), jsonMapper, new PlatnoJson(), zadaci, clock);
    }

    Ucesnik ucesnik(Long id, String ime) {
        Ucesnik u = new Ucesnik();
        u.setId(id);
        u.setIme(ime);
        u.setIzvodjenje(iz);
        u.setKreirano(T0.plusSeconds(id));
        ucesnici.add(u);
        return u;
    }

    ZadatakSnimak snimak() {
        return new ZadatakSnimak(SLAJD, 3L, "2 → 10", List.of(new GrupaPutanja(3L, "2 → 10")), null, List.of(
                new VarijantaSnimak(VAR_A, "V-A", 3L, "svg-A", "res-A", POLJA,
                        Map.of("a", new OdgovorPolja(4545.0, null, null, null)), 3),
                new VarijantaSnimak(VAR_B, "V-B", 3L, "svg-B", "res-B", POLJA,
                        Map.of("a", new OdgovorPolja(1313.0, null, null, null)), 3)), 3);
    }

    /** Faza ZADATAK slajda; van CEKA runda 202 sa odgovorima oba učesnika. */
    void faza(Faza faza) {
        iz.setFaza(faza);
        runde.clear();
        odgovori.clear();
        if (faza == Faza.CEKA) {
            iz.setTrenutnaRundaId(null);
            return;
        }
        PitanjeRunda r = new PitanjeRunda();
        r.setId(RUNDA);
        r.setIzvodjenje(iz);
        r.setSlajdId(SLAJD);
        r.setRedniBroj(1);
        r.setOtvoreno(T0);
        r.setSnimak(jsonMapper.writeValueAsString(snimak()));
        if (faza == Faza.ZATVORENO) r.setZatvoreno(T0.plusSeconds(4));
        runde.add(r);
        iz.setTrenutnaRundaId(RUNDA);
        odgovor(r, u1, VAR_A, "4545", true);
        odgovor(r, u2, VAR_B, "1", false);
    }

    void odgovor(PitanjeRunda r, Ucesnik u, Long varijanta, String vrednost, boolean tacno) {
        Odgovor o = new Odgovor();
        o.setId(400L + odgovori.size());
        o.setRunda(r);
        o.setUcesnik(u);
        o.setVarijantaId(varijanta);
        o.setTacno(tacno);
        o.setPolja(jsonMapper.writeValueAsString(Map.of("a", new PoljeOdgovora(vrednost, tacno))));
        o.setVremeMs(1000L);
        o.setKreirano(T0.plusSeconds(2));
        odgovori.add(o);
    }

    String json(Object o) {
        return jsonMapper.writeValueAsString(o).toLowerCase(Locale.ROOT);
    }

    void javnoBezTajni() {
        String j = json(service.javno(IZ));
        for (String t : List.of("svg-", "res-", "4545", "1313", "primer-svg")) {
            assertFalse(j.contains(t), () -> "javno stanje odaje " + t + ": " + j);
        }
    }

    // ---------------------------------------------------------------- telefon

    @Test
    void telefonPoFazama() {
        record Slucaj(Faza faza, boolean tacan, boolean resenje) {}
        for (Slucaj c : List.of(new Slucaj(Faza.CEKA, false, false), new Slucaj(Faza.OTVORENO, false, false),
                new Slucaj(Faza.ZATVORENO, false, false), new Slucaj(Faza.ZATVORENO, true, false),
                new Slucaj(Faza.ZATVORENO, false, true))) {
            faza(c.faza());
            iz.setTacanPrikazan(c.tacan());
            iz.setResenjePrikazano(c.resenje());
            String opis = c.toString();

            javnoBezTajni();
            LicnoStanje l = service.licno(IZ, 1L);
            String j = json(l);
            for (String t : List.of("svg-b", "res-b", "1313", "primer-svg")) {
                assertFalse(j.contains(t), opis + ": lično odaje tuđu varijantu " + t);
            }
            assertEquals(c.faza() != Faza.CEKA, j.contains("svg-a"), opis);
            assertEquals(c.resenje(), j.contains("res-a"), opis);
            assertEquals(c.tacan(), j.contains("4545"), opis);
            if (c.faza() == Faza.CEKA) {
                assertNull(l.zadatak());
                assertEquals(4, service.javno(IZ).zadatak().brojVarijanti());
                assertNull(service.javno(IZ).zadatak().rundaId());
            } else {
                assertEquals(VAR_A, l.zadatak().varijantaId());
                assertTrue(l.zadatak().primljen());
                assertEquals(c.faza() == Faza.ZATVORENO, l.zadatak().ishod() != null, opis);
                assertEquals(c.faza() == Faza.ZATVORENO ? Boolean.TRUE : null, l.zadatak().tacno(), opis);
                assertEquals(2, service.javno(IZ).zadatak().brojVarijanti(), "posle otvaranja: izvučeno");
                assertEquals(c.resenje(), service.javno(IZ).zadatak().resenjePrikazano());
            }
            String j2 = json(service.licno(IZ, 2L));
            assertFalse(j2.contains("svg-a") || j2.contains("res-a") || j2.contains("4545"), opis);
        }
    }

    @Test
    void pocetnoIstaVarijantaBezResenja() {
        for (Faza f : List.of(Faza.OTVORENO, Faza.ZATVORENO)) {
            faza(f);
            PocetnoStanje p = service.pocetno(IZ, 1L);
            String j = json(p);
            assertTrue(j.contains("svg-a"), f.toString());
            assertFalse(j.contains("res-"), f.toString());
            assertFalse(j.contains("svg-b"), f.toString());
            assertFalse(j.contains("primer-svg"), f.toString() + ": primer iz editora ne ide na telefon");
            assertEquals(VAR_A, p.licno().zadatak().varijantaId());
        }
    }

    // ---------------------------------------------------------------- konzola i projektor

    @Test
    void konzolaIProjektor() {
        faza(Faza.ZATVORENO);
        NastavnickoStanje n = service.nastavnicko(IZ);
        NastavnickiZadatak z = n.zadatak();
        assertNotNull(z);
        assertTrue(json(z.rezultat()).contains("res-a") && json(z.rezultat()).contains("res-b"), "konzola vidi rešenja");
        assertTrue(json(z.rezultat()).contains("4545"), "konzola vidi tačno");
        assertEquals(2, z.varijante().size());
        assertEquals(1, z.varijante().getFirst().tacnih());
        assertEquals(VAR_A, n.ucesnici().getFirst().varijantaId());
        assertEquals(Boolean.TRUE, n.ucesnici().getFirst().tacno());
        assertEquals(VAR_B, n.ucesnici().get(1).varijantaId());

        String javni = json(z.javniRezultat());
        assertFalse(javni.contains("res-"), "projektor bez rešenja pre RESENJE");
        assertFalse(javni.contains("primer-svg"), "primer iz editora nije u rezultatu projektora");
        assertNull(z.javniRezultat().varijante().getFirst().polja().getFirst().tacno(), "projektor bez tačnog pre TACAN");
        assertFalse(javni.contains("1313"), "tačno za B (niko ga nije uneo) se ne vidi pre TACAN");
        assertNull(z.javniRezultat().varijante().getFirst().tacnih());
        assertEquals(1, z.javniRezultat().varijante().getFirst().odgovorilo());
        assertTrue(z.javniRezultat().odgovoriPrikazani());

        iz.setTacanPrikazan(true);
        z = service.nastavnicko(IZ).zadatak();
        assertEquals(1, z.javniRezultat().varijante().getFirst().tacnih());
        assertFalse(json(z.javniRezultat()).contains("res-"));

        iz.setResenjePrikazano(true);
        z = service.nastavnicko(IZ).zadatak();
        assertTrue(json(z.javniRezultat()).contains("res-a"));
        javnoBezTajni();
    }

    @Test
    void prekidaciNezavisno() {
        faza(Faza.ZATVORENO);
        iz.setRezultatiPrikazani(false);
        assertNull(service.nastavnicko(IZ).zadatak().javniRezultat(), "nijedan prekidač: projektor bez rezultata");
        javnoBezTajni();

        iz.setResenjePrikazano(true);
        ZadatakRezultat r = service.nastavnicko(IZ).zadatak().javniRezultat();
        String j = json(r);
        assertTrue(j.contains("res-a") && j.contains("res-b"));
        assertFalse(r.odgovoriPrikazani());
        assertEquals(0, r.varijante().getFirst().odgovorilo());
        assertNull(r.varijante().getFirst().polja().getFirst().vrednosti());
        assertFalse(j.contains("4545"));
        javnoBezTajni();

        iz.setResenjePrikazano(false);
        iz.setTacanPrikazan(true);
        r = service.nastavnicko(IZ).zadatak().javniRezultat();
        j = json(r);
        assertEquals(4545.0, r.varijante().getFirst().polja().getFirst().tacno().broj());
        assertFalse(j.contains("res-"));
        assertFalse(r.odgovoriPrikazani());
        assertNull(r.varijante().getFirst().tacnih());
        javnoBezTajni();
    }

    @Test
    void cekaBrojZaOtvaranje() {
        faza(Faza.CEKA);
        NastavnickiZadatak z = service.nastavnicko(IZ).zadatak();
        assertEquals(4, z.brojZaOtvaranje());
        assertNull(z.varijante());
        assertNull(z.rezultat());
        assertNull(z.javniRezultat());
        iz.setZadatakBrojVarijanti(0);
        assertEquals(6, service.nastavnicko(IZ).zadatak().brojZaOtvaranje(), "0 = sve aktivne");
        iz.setZadatakBrojVarijanti(50);
        assertEquals(6, service.nastavnicko(IZ).zadatak().brojZaOtvaranje(), "najviše koliko ima kandidata");
    }

    @Test
    void nivoVidiSamoKonzola() {
        slajd.setZadatakNivo(3);
        for (Faza f : List.of(Faza.CEKA, Faza.OTVORENO, Faza.ZATVORENO)) {
            faza(f);
            iz.setTacanPrikazan(true);
            iz.setResenjePrikazano(f == Faza.ZATVORENO);
            for (Object telefon : List.of(service.javno(IZ), service.licno(IZ, 1L), service.pocetno(IZ, 1L))) {
                assertFalse(json(telefon).contains("nivo"), () -> f + ": telefon dobija nivo: " + json(telefon));
            }
            NastavnickiZadatak z = service.nastavnicko(IZ).zadatak();
            assertEquals(3, z.nivoZaOtvaranje(), f.toString());
            if (z.javniRezultat() != null) {
                assertFalse(json(z.javniRezultat()).contains("nivo"), f + ": projektor dobija nivo");
            }
            if (f != Faza.CEKA) {
                assertEquals(3, z.varijante().getFirst().nivo(), "konzola vidi nivo varijante");
            }
        }
    }

    /** Uneto po učesniku ({@code UcesnikZadatka.uneto}) nosi samo pregled izvođenja: ni telefon ni projektor ga ne dobijaju. */
    @Test
    void unetoNosiSamoPregled() {
        for (Faza f : List.of(Faza.CEKA, Faza.OTVORENO, Faza.ZATVORENO)) {
            faza(f);
            iz.setTacanPrikazan(true);
            iz.setResenjePrikazano(f == Faza.ZATVORENO);
            iz.setRezultatiPrikazani(true);
            for (Object telefon : List.of(service.javno(IZ), service.licno(IZ, 1L), service.pocetno(IZ, 1L))) {
                assertFalse(json(telefon).contains("uneto"), () -> f + ": telefon dobija uneto: " + json(telefon));
            }
            NastavnickiZadatak z = service.nastavnicko(IZ).zadatak();
            if (z.javniRezultat() != null) {
                assertFalse(json(z.javniRezultat()).contains("uneto"), f + ": projektor dobija uneto");
            }
        }
    }
}
