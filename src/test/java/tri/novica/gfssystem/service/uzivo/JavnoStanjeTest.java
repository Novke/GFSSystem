package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Jezgro "ni ranije": {@link StanjeService#javno}, {@code licno}, {@code licnaZaSve} i {@code pocetno} nad mock
 * repozitorijumima. Svi tekstovi pitanja (tekst, opcije, oznake skale, slika) sadrže "tajn", pa provera nad JSON-om
 * ({@link #bezTajni}) dokazuje da ih server ne šalje dok nisu dozvoljeni. Jedinica broja nije tajna (ide uvek od
 * otvaranja), pa je {@value #JEDINICA}. Javne projekcije u nastavničkom stanju (projektor) moraju biti iste kao javno
 * stanje ({@link #projektorKaoJavno}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JavnoStanjeTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 8, 12, 0);
    static final Long IZ = 5L;
    static final Long RUNDA = 202L;
    static final String JEDINICA = "m/s²";

    @Mock IzvodjenjeRepository izvodjenjeRepository;
    @Mock SlajdRepository slajdRepository;
    @Mock PitanjeRundaRepository rundaRepository;
    @Mock OdgovorRepository odgovorRepository;
    @Mock UcesnikRepository ucesnikRepository;

    final MutableClock clock = new MutableClock(T0.plusSeconds(5).atZone(ZONA).toInstant(), ZONA);
    final JsonMapper jsonMapper = JsonMapper.builder().build();
    StanjeService service;

    Izvodjenje iz;
    Slajd info;
    final Map<TipPitanja, Slajd> pitanja = new java.util.EnumMap<>(TipPitanja.class);
    final List<Slajd> slajdovi = new ArrayList<>();
    final List<PitanjeRunda> runde = new ArrayList<>();
    final List<Ucesnik> ucesnici = new ArrayList<>();
    final List<Odgovor> odgovori = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Predmet predmet = new Predmet();
        predmet.setId(7L);
        Prezentacija prez = new Prezentacija();
        prez.setId(1L);
        prez.setNaziv("Uvod u statiku");
        prez.setPredmet(predmet);

        info = new Slajd();
        info.setId(11L);
        info.setRb(1);
        info.setTip(TipSlajda.INFO);
        info.setNaslov("Tajni naslov");
        info.setSadrzaj("Tajni sadržaj");
        slajdovi.add(info);
        long id = 12;
        for (TipPitanja tip : TipPitanja.values()) {
            Slajd s = pitanje(id, (int) id - 10, tip);
            pitanja.put(tip, s);
            slajdovi.add(s);
            id++;
        }

        iz = new Izvodjenje();
        iz.setId(IZ);
        iz.setPrezentacija(prez);
        iz.setKod("123456");
        iz.setAktivanKod("123456");
        iz.setStatus(StatusIzvodjenja.AKTIVNO);
        iz.setPocetak(T0);
        iz.setPrikaz(Prikaz.PRIJAVA);
        iz.setEkran(Ekran.NORMALAN);
        iz.setTelefonPrikaz(TelefonPrikaz.DUGMAD);
        iz.setDetaljiDozvoljeni(false);
        iz.setTakmicenje(true);
        iz.setVerzija(17);

        when(izvodjenjeRepository.findSaVezama(IZ)).thenReturn(Optional.of(iz));
        when(slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(1L)).thenReturn(slajdovi);
        when(rundaRepository.findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(IZ)).thenReturn(runde);
        when(ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(IZ)).thenReturn(ucesnici);
        when(odgovorRepository.findAllByRundaIzvodjenjeId(IZ)).thenReturn(odgovori);
        when(ucesnikRepository.findByIdAndIzvodjenjeId(anyLong(), eq(IZ))).thenAnswer(i -> ucesnici.stream()
                .filter(u -> u.getId().equals(i.getArgument(0))).findFirst());

        service = new StanjeService(izvodjenjeRepository, slajdRepository, rundaRepository, odgovorRepository,
                ucesnikRepository, new PovezanostRegistar(), jsonMapper, clock);
    }

    Slajd pitanje(Long id, int rb, TipPitanja tip) {
        Pitanje p = new Pitanje();
        p.setId(id * 10);
        p.setTip(tip);
        p.setTekst("Tajno pitanje " + id);
        Medij slika = new Medij();
        slika.setId("tajna-slika-" + id);
        p.setSlika(slika);
        switch (tip) {
            case JEDAN_TACAN, VISE_TACNIH, ANKETA, TACNO_NETACNO -> {
                int n = tip == TipPitanja.TACNO_NETACNO ? 2 : 3;
                for (int i = 0; i < n; i++) {
                    PitanjeOpcija o = new PitanjeOpcija();
                    o.setId(id * 10 + i + 1);
                    o.setRb(i + 1);
                    o.setTekst("Tajna opcija " + (i + 1));
                    o.setTacna(tip != TipPitanja.ANKETA && (i == 0 || tip == TipPitanja.VISE_TACNIH && i == 2));
                    p.getOpcije().add(o);
                }
            }
            case BROJ -> {
                p.setBrojTacno(9.81);
                p.setBrojOdstupanje(0.1);
                p.setOdstupanjeTip(OdstupanjeTip.APSOLUTNO);
                p.setJedinica(JEDINICA);
            }
            case KRATAK_TEKST -> {
                p.setTekstPrikaz(TekstPrikaz.OBLAK);
                p.setPrihvatljiviOdgovori(List.of("Njutn"));
            }
            case SKALA -> {
                p.setSkalaMinOznaka("Tajna oznaka min");
                p.setSkalaMaxOznaka("Tajna oznaka max");
            }
        }
        p.setVremeSekunde(20);
        Slajd s = new Slajd();
        s.setId(id);
        s.setRb(rb);
        s.setTip(TipSlajda.PITANJE);
        s.setPitanje(p);
        return s;
    }

    /** Trenutni slajd je pitanje tipa {@code tip} u fazi {@code faza}; van CEKA postoji runda 202 sa rokom T0+20 s. */
    Slajd na(TipPitanja tip, Faza faza) {
        Slajd s = pitanja.get(tip);
        iz.setPrikaz(Prikaz.SLAJD);
        iz.setTrenutniSlajdId(s.getId());
        iz.setFaza(faza);
        if (faza == Faza.CEKA) {
            iz.setTrenutnaRundaId(null);
            return s;
        }
        PitanjeRunda r = runda(RUNDA, s, 1);
        if (faza == Faza.OTVORENO) {
            r.setRok(T0.plusSeconds(20));
            r.setTrajanjeMs(20_000L);
        } else {
            r.setZatvoreno(T0.plusSeconds(4));
        }
        iz.setTrenutnaRundaId(RUNDA);
        return s;
    }

    PitanjeRunda runda(Long id, Slajd s, int redniBroj) {
        PitanjeRunda r = new PitanjeRunda();
        r.setId(id);
        r.setIzvodjenje(iz);
        r.setSlajdId(s.getId());
        r.setRedniBroj(redniBroj);
        r.setOtvoreno(T0.plusSeconds(redniBroj - 1));
        r.setSnimak(jsonMapper.writeValueAsString(PitanjeSnimak.od(s)));
        runde.add(r);
        return r;
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

    Odgovor odgovor(PitanjeRunda r, Ucesnik u, int poeni, Boolean tacno) {
        Odgovor o = new Odgovor();
        o.setId(400L + odgovori.size());
        o.setRunda(r);
        o.setUcesnik(u);
        o.setTacno(tacno);
        o.setPoeni(poeni);
        o.setVremeMs(1000L * (odgovori.size() + 1));
        o.setKreirano(r.getOtvoreno().plusSeconds(odgovori.size() + 1));
        odgovori.add(o);
        return o;
    }

    PitanjeRunda trenutna() {
        return runde.stream().filter(r -> r.getId().equals(RUNDA)).findFirst().orElseThrow();
    }

    /** Serijalizovano stanje (ono što zaista ide na mrežu) ne sadrži nijedan tekst pitanja. */
    void bezTajni(Object stanje) {
        String json = jsonMapper.writeValueAsString(stanje).toLowerCase(Locale.ROOT);
        assertFalse(json.contains("tajn"), () -> "tekst pitanja procureo: " + json);
    }

    /**
     * Projektor (nastavničko stanje) prikazuje javni rezultat i javnu rang-listu: moraju biti tačno ono što server
     * šalje telefonima u javnom stanju, inače bi `Space` ili `L` na projektoru odali tačnost pre `C`.
     */
    void projektorKaoJavno(JavnoStanje st) {
        NastavnickoStanje n = service.nastavnicko(IZ);
        assertEquals(st.rezultat(), n.javniRezultat());
        assertEquals(st.rangLista(), n.javnaRangLista());
    }

    static void samoTipIFaza(JavnoPitanje p, TipPitanja tip, Faza faza) {
        assertEquals(new JavnoPitanje(tip, faza, null, null, null, null, null, null, null, null, null, null, null,
                null, null), p);
    }

    // ---------------------------------------------------------------- osnovna polja, prijava, info

    @Test
    void prijavaBezPitanja() {
        Ucesnik ana = ucesnik(31L, "Ana");
        Ucesnik izbacen = ucesnik(32L, "Bojan");
        izbacen.setIzbacen(true);

        JavnoStanje st = service.javno(IZ);

        assertEquals(IZ, st.izvodjenjeId());
        assertEquals(17, st.verzija());
        assertEquals(clock.millis(), st.serverVremeMs());
        assertEquals(StatusIzvodjenja.AKTIVNO, st.status());
        assertEquals("Uvod u statiku", st.naziv());
        assertEquals("123456", st.kod());
        assertEquals(Prikaz.PRIJAVA, st.prikaz());
        assertNull(st.slajdTip());
        assertEquals(Ekran.NORMALAN, st.ekran());
        assertTrue(st.takmicenje());
        assertEquals(TelefonPrikaz.DUGMAD, st.telefonPrikaz());
        assertFalse(st.detaljiDozvoljeni());
        assertEquals(1, st.brojUcesnika());
        assertNull(st.pitanje());
        assertNull(st.rezultat());
        assertNull(st.rangLista());
        assertNotNull(ana);
        bezTajni(st);
    }

    @Test
    void infoSlajdNeSaljeSadrzaj() {
        iz.setPrikaz(Prikaz.SLAJD);
        iz.setTrenutniSlajdId(11L);
        iz.setTelefonPrikaz(TelefonPrikaz.PITANJE);
        iz.setDetaljiDozvoljeni(true);

        JavnoStanje st = service.javno(IZ);

        assertEquals(TipSlajda.INFO, st.slajdTip());
        assertNull(st.pitanje());
        bezTajni(st);
    }

    // ---------------------------------------------------------------- CEKA: samo {tip, faza}

    @ParameterizedTest
    @EnumSource(TipPitanja.class)
    void cekaSaljeSamoTipIFazu(TipPitanja tip) {
        na(tip, Faza.CEKA);
        // ni potpuno otvoren telefon ni zaostale zastavice ne otkrivaju ništa pre otvaranja
        iz.setTelefonPrikaz(TelefonPrikaz.PITANJE);
        iz.setDetaljiDozvoljeni(true);
        iz.setTacanPrikazan(true);
        iz.setRezultatiPrikazani(true);

        JavnoStanje st = service.javno(IZ);

        assertEquals(TipSlajda.PITANJE, st.slajdTip());
        samoTipIFaza(st.pitanje(), tip, Faza.CEKA);
        assertNull(st.rezultat());
        bezTajni(st);
    }

    @Test
    void cekaSaZaostalimIdjemRundeNeOtkrivaNista() {
        // i kad bi id runde zaostao u CEKA, telefon ne dobija ništa iz te runde
        na(TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        iz.setFaza(Faza.CEKA);
        iz.setTelefonPrikaz(TelefonPrikaz.PITANJE);
        iz.setRezultatiPrikazani(true);
        odgovor(trenutna(), ucesnik(31L, "Ana"), 1000, true).setOpcije("121");

        JavnoStanje st = service.javno(IZ);

        samoTipIFaza(st.pitanje(), TipPitanja.JEDAN_TACAN, Faza.CEKA);
        assertNull(st.rezultat());
        bezTajni(st);
        assertNull(service.licno(IZ, 31L).odgovor());
    }

    @Test
    void rundaDrugogSlajdaSeNePrikazuje() {
        // trenutna runda pripada drugom slajdu (ne bi smelo da se desi): ništa iz nje
        na(TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        iz.setTrenutniSlajdId(pitanja.get(TipPitanja.ANKETA).getId());
        iz.setTelefonPrikaz(TelefonPrikaz.PITANJE);
        JavnoStanje st = service.javno(IZ);
        samoTipIFaza(st.pitanje(), TipPitanja.ANKETA, Faza.ZATVORENO);
        bezTajni(st);
    }

    @Test
    void cekaSaFazomNullJeCeka() {
        na(TipPitanja.JEDAN_TACAN, Faza.CEKA);
        iz.setFaza(null);
        samoTipIFaza(service.javno(IZ).pitanje(), TipPitanja.JEDAN_TACAN, Faza.CEKA);
    }

    @Test
    void zatvorenoBezRundeNeOtkrivaNista() {
        na(TipPitanja.JEDAN_TACAN, Faza.CEKA);
        iz.setFaza(Faza.ZATVORENO);
        iz.setTelefonPrikaz(TelefonPrikaz.PITANJE);
        JavnoStanje st = service.javno(IZ);
        samoTipIFaza(st.pitanje(), TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        bezTajni(st);
    }

    // ---------------------------------------------------------------- OTVORENO: id-jevi da, tekst tek kad je dozvoljen

    @Test
    void otvorenoDugmadBezDetalja() {
        na(TipPitanja.JEDAN_TACAN, Faza.OTVORENO);

        JavnoStanje st = service.javno(IZ);
        JavnoPitanje p = st.pitanje();

        assertEquals(TipPitanja.JEDAN_TACAN, p.tip());
        assertEquals(Faza.OTVORENO, p.faza());
        assertEquals(RUNDA, p.rundaId());
        assertEquals(3, p.brojOpcija());
        assertEquals(List.of(new JavnaOpcija(121L, null), new JavnaOpcija(122L, null), new JavnaOpcija(123L, null)),
                p.opcije());
        assertNull(p.tekst());
        assertNull(p.slikaId());
        assertNull(p.jedinica());
        assertNull(p.skalaMinOznaka());
        assertNull(p.skalaMaxOznaka());
        assertEquals(T0.plusSeconds(20).atZone(ZONA).toInstant().toEpochMilli(), p.rokMs());
        assertNull(p.preostaloMs());
        assertNull(p.tacneOpcije());
        assertNull(p.tacanBroj());
        assertNull(p.prihvatljiviOdgovori());
        assertNull(st.rezultat());
        bezTajni(st);
    }

    @ParameterizedTest
    @EnumSource(TipPitanja.class)
    void otvorenoDugmadBezDetaljaZaSveTipove(TipPitanja tip) {
        na(tip, Faza.OTVORENO);
        JavnoStanje st = service.javno(IZ);
        assertEquals(RUNDA, st.pitanje().rundaId());
        bezTajni(st);
    }

    @ParameterizedTest
    @EnumSource(TipPitanja.class)
    void zatvorenoDugmadBezDetaljaZaSveTipove(TipPitanja tip) {
        na(tip, Faza.ZATVORENO);
        JavnoStanje st = service.javno(IZ);
        assertEquals(Faza.ZATVORENO, st.pitanje().faza());
        assertNull(st.pitanje().rokMs());
        bezTajni(st);
    }

    @Test
    void otvorenoSaDozvoljenimDetaljima() {
        na(TipPitanja.JEDAN_TACAN, Faza.OTVORENO);
        iz.setDetaljiDozvoljeni(true);

        JavnoPitanje p = service.javno(IZ).pitanje();

        assertEquals("Tajno pitanje 12", p.tekst());
        assertEquals("tajna-slika-12", p.slikaId());
        assertEquals(List.of(new JavnaOpcija(121L, "Tajna opcija 1"), new JavnaOpcija(122L, "Tajna opcija 2"),
                new JavnaOpcija(123L, "Tajna opcija 3")), p.opcije());
        assertNull(p.tacneOpcije());
    }

    @Test
    void otvorenoURezimuPitanje() {
        na(TipPitanja.SKALA, Faza.OTVORENO);
        iz.setTelefonPrikaz(TelefonPrikaz.PITANJE);

        JavnoPitanje p = service.javno(IZ).pitanje();

        assertEquals("Tajno pitanje " + pitanja.get(TipPitanja.SKALA).getId(), p.tekst());
        assertEquals("Tajna oznaka min", p.skalaMinOznaka());
        assertEquals("Tajna oznaka max", p.skalaMaxOznaka());
        assertNull(p.brojOpcija());
        assertNull(p.opcije());
    }

    @Test
    void brojJedinicaUvekOdOtvaranja() {
        // jedinica nije tajna: student mora da zna u čemu upisuje broj i u režimu DUGMAD bez Detalja
        na(TipPitanja.BROJ, Faza.CEKA);
        assertNull(service.javno(IZ).pitanje().jedinica());

        na(TipPitanja.BROJ, Faza.OTVORENO);
        JavnoPitanje bez = service.javno(IZ).pitanje();
        assertEquals(JEDINICA, bez.jedinica());
        assertNull(bez.tekst());
        assertNull(bez.opcije());
        assertNull(bez.brojOpcija());

        iz.setDetaljiDozvoljeni(true);
        assertEquals(JEDINICA, service.javno(IZ).pitanje().jedinica());
        iz.setDetaljiDozvoljeni(false);
        iz.setTelefonPrikaz(TelefonPrikaz.PITANJE);
        assertEquals(JEDINICA, service.javno(IZ).pitanje().jedinica());
    }

    @Test
    void tekstIzSnimkaRundeANeSaSlajda() {
        // slajd izmenjen posle otvaranja: telefon dobija ono na šta se odgovara (snimak)
        Slajd s = na(TipPitanja.JEDAN_TACAN, Faza.OTVORENO);
        iz.setDetaljiDozvoljeni(true);
        s.getPitanje().setTekst("Izmenjeno posle otvaranja");
        assertEquals("Tajno pitanje 12", service.javno(IZ).pitanje().tekst());
    }

    @Test
    void pauziranTajmer() {
        na(TipPitanja.JEDAN_TACAN, Faza.OTVORENO);
        trenutna().setRok(null);
        trenutna().setPreostaloMs(12_000L);
        JavnoPitanje p = service.javno(IZ).pitanje();
        assertNull(p.rokMs());
        assertEquals(12_000L, p.preostaloMs());
    }

    @Test
    void povratakNaPitanjeSaRundomJeZatvoren() {
        na(TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        JavnoPitanje p = service.javno(IZ).pitanje();
        assertEquals(RUNDA, p.rundaId());
        assertEquals(3, p.opcije().size());
        assertNull(p.rokMs());
        assertNull(p.preostaloMs());
    }

    // ---------------------------------------------------------------- tačan odgovor tek posle TACAN

    @Test
    void tacneOpcijeTekKadJeTacanPrikazan() {
        na(TipPitanja.VISE_TACNIH, Faza.ZATVORENO);
        assertNull(service.javno(IZ).pitanje().tacneOpcije());

        iz.setTacanPrikazan(true);
        JavnoStanje st = service.javno(IZ);
        assertEquals(List.of(131L, 133L), st.pitanje().tacneOpcije());
        // tačne opcije ne otkrivaju tekst kad tekst nije dozvoljen
        bezTajni(st);
    }

    @Test
    void anketaNemaTacnihOpcija() {
        na(TipPitanja.ANKETA, Faza.ZATVORENO);
        iz.setTacanPrikazan(true);
        assertNull(service.javno(IZ).pitanje().tacneOpcije());
    }

    @Test
    void tacanBrojTekKadJeTacanPrikazan() {
        na(TipPitanja.BROJ, Faza.ZATVORENO);
        assertNull(service.javno(IZ).pitanje().tacanBroj());
        iz.setTacanPrikazan(true);
        assertEquals(9.81, service.javno(IZ).pitanje().tacanBroj());
    }

    @Test
    void prihvatljiviTekKadJeTacanPrikazan() {
        na(TipPitanja.KRATAK_TEKST, Faza.ZATVORENO);
        assertNull(service.javno(IZ).pitanje().prihvatljiviOdgovori());
        iz.setTacanPrikazan(true);
        assertEquals(List.of("Njutn"), service.javno(IZ).pitanje().prihvatljiviOdgovori());
    }

    @ParameterizedTest
    @EnumSource(TipPitanja.class)
    void tacanNikadDokJePitanjeOtvoreno(TipPitanja tip) {
        // stanje-mašina to ne dozvoljava; i kad bi zastavica ostala, otvoreno pitanje ne otkriva tačan odgovor
        Slajd s = na(tip, Faza.OTVORENO);
        iz.setTacanPrikazan(true);
        iz.setRezultatiPrikazani(true);
        Ucesnik ana = ucesnik(31L, "Ana");
        Odgovor o = odgovor(trenutna(), ana, 1000, true);
        o.setOpcije(s.getPitanje().getOpcije().isEmpty() ? null : String.valueOf(s.getPitanje().getOpcije().get(0).getId()));
        o.setTekst(tip == TipPitanja.KRATAK_TEKST ? "Njutn" : null);

        JavnoStanje st = service.javno(IZ);

        assertNull(st.pitanje().tacneOpcije());
        assertNull(st.pitanje().tacanBroj());
        assertNull(st.pitanje().prihvatljiviOdgovori());
        if (st.rezultat().opcije() != null) st.rezultat().opcije().forEach(r -> assertNull(r.tacna()));
        if (st.rezultat().tekstovi() != null) st.rezultat().tekstovi().forEach(r -> assertNull(r.tacan()));
        LicnoStanje l = service.licno(IZ, 31L);
        assertTrue(l.odgovor().primljen());
        assertNull(l.odgovor().tacno());
        assertNull(l.odgovor().poeni());
    }

    /** Sve kombinacije: tip pitanja x faza x režim telefona x Detalji x zastavice rezultata, tačnog i rang-liste. */
    static Stream<Arguments> svaStanja() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (TipPitanja tip : TipPitanja.values())
            for (Faza faza : Faza.values())
                for (TelefonPrikaz tp : TelefonPrikaz.values())
                    for (boolean detalji : new boolean[]{false, true})
                        for (boolean rezultati : new boolean[]{false, true})
                            for (boolean tacan : new boolean[]{false, true})
                                for (boolean rang : new boolean[]{false, true})
                                    b.add(Arguments.of(tip, faza, tp, detalji, rezultati, tacan, rang));
        return b.build();
    }

    /**
     * Jezgro "ni ranije", iscrpno: ni u jednoj kombinaciji faze, tipa i zastavica telefon ne dobija tekst pre dozvole,
     * tačan odgovor (ni kroz rezultat ni kroz lično stanje) pre nego što je prikazan na zatvorenom pitanju, rezultat
     * pre prikaza, ni rang-listu pre prikaza.
     */
    @ParameterizedTest(name = "{0} {1} {2} detalji={3} rezultati={4} tacan={5} rang={6}")
    @MethodSource("svaStanja")
    void niRanijeUSvimStanjima(TipPitanja tip, Faza faza, TelefonPrikaz tp, boolean detalji, boolean rezultati,
                               boolean tacan, boolean rang) {
        Slajd s = na(tip, faza);
        iz.setTelefonPrikaz(tp);
        iz.setDetaljiDozvoljeni(detalji);
        iz.setRezultatiPrikazani(rezultati);
        iz.setTacanPrikazan(tacan);
        iz.setRangListaPrikazana(rang);
        Ucesnik ana = ucesnik(31L, "Ana");
        // Bojan ima 300 sa ranijeg slajda: to se uvek računa
        Ucesnik bojan = ucesnik(32L, "Bojan");
        Slajd raniji = pitanja.get(tip == TipPitanja.JEDAN_TACAN ? TipPitanja.ANKETA : TipPitanja.JEDAN_TACAN);
        odgovor(runda(201L, raniji, 1), bojan, 300, true);
        if (faza != Faza.CEKA) {
            Odgovor o = odgovor(trenutna(), ana, 750, true);
            switch (tip) {
                case JEDAN_TACAN, VISE_TACNIH, ANKETA, TACNO_NETACNO ->
                        o.setOpcije(String.valueOf(s.getPitanje().getOpcije().get(0).getId()));
                case BROJ -> o.setBroj(9.81);
                case KRATAK_TEKST -> o.setTekst("Njutn");
                case SKALA -> o.setSkala(3);
            }
        }
        boolean tekstDozvoljen = tp == TelefonPrikaz.PITANJE || detalji;
        boolean tacanVidljiv = tacan && faza == Faza.ZATVORENO;

        JavnoStanje st = service.javno(IZ);
        LicnoStanje l = service.licno(IZ, 31L);
        JavnoPitanje p = st.pitanje();

        assertEquals(tip, p.tip());
        assertEquals(faza, p.faza());
        if (faza == Faza.CEKA) {
            samoTipIFaza(p, tip, faza);
            assertNull(st.rezultat());
            assertNull(l.odgovor());
        } else {
            assertEquals(RUNDA, p.rundaId());
            assertEquals(RUNDA, l.odgovor().rundaId());
            assertTrue(l.odgovor().primljen());
            // jedinica broja ide u svakom režimu telefona (nije tajna), ostali tipovi je nemaju
            assertEquals(tip == TipPitanja.BROJ ? JEDINICA : null, p.jedinica());
        }
        if (!tekstDozvoljen) {
            bezTajni(p);
            if (st.rezultat() == null) bezTajni(st);
        }
        if (!tacanVidljiv) {
            assertNull(p.tacneOpcije());
            assertNull(p.tacanBroj());
            assertNull(p.prihvatljiviOdgovori());
            if (l.odgovor() != null) {
                assertNull(l.odgovor().tacno());
                assertNull(l.odgovor().poeni());
            }
            Rezultat r = st.rezultat();
            if (r != null) {
                if (r.opcije() != null) r.opcije().forEach(o -> assertNull(o.tacna()));
                if (r.tekstovi() != null) r.tekstovi().forEach(t -> assertNull(t.tacan()));
                if (r.brojevi() != null) assertNull(r.brojevi().uOdstupanju());
            }
        }
        if (!rezultati) assertNull(st.rezultat());
        if (!rang) assertNull(st.rangLista());
        // projektor: isti javni rezultat i rang-lista kao telefoni, u svakoj kombinaciji
        projektorKaoJavno(st);

        // poeni, mesto i rang-lista ne odaju tačnost: Anin tačan odgovor u trenutnoj rundi se računa tek posle TACAN
        boolean anaSeRacuna = faza != Faza.CEKA && tacanVidljiv;
        assertEquals(anaSeRacuna ? 750 : 0, l.poeni());
        assertEquals(anaSeRacuna ? 1 : 2, l.mesto());
        assertEquals(300, service.licno(IZ, 32L).poeni());
        assertEquals(service.licnaZaSve(IZ).get(31L), l);
        if (rang) {
            assertEquals(anaSeRacuna
                    ? List.of(new RangStavka(1, null, "Ana", 750), new RangStavka(2, null, "Bojan", 300))
                    : List.of(new RangStavka(1, null, "Bojan", 300), new RangStavka(2, null, "Ana", 0)), st.rangLista());
        }
    }

    // ---------------------------------------------------------------- rezultat

    @Test
    void rezultatTekKadJePrikazan() {
        na(TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        Ucesnik ana = ucesnik(31L, "Ana");
        odgovor(trenutna(), ana, 1000, true).setOpcije("121");
        assertNull(service.javno(IZ).rezultat());

        iz.setRezultatiPrikazani(true);
        Rezultat r = service.javno(IZ).rezultat();
        assertEquals(1, r.ukupno());
        assertEquals(List.of(new RezultatOpcija(121L, "Tajna opcija 1", 1, null),
                new RezultatOpcija(122L, "Tajna opcija 2", 0, null),
                new RezultatOpcija(123L, "Tajna opcija 3", 0, null)), r.opcije());

        iz.setTacanPrikazan(true);
        assertEquals(List.of(true, false, false),
                service.javno(IZ).rezultat().opcije().stream().map(RezultatOpcija::tacna).toList());
    }

    @Test
    void javniRezultatBezSakrivenihTekstova() {
        na(TipPitanja.KRATAK_TEKST, Faza.ZATVORENO);
        iz.setRezultatiPrikazani(true);
        Ucesnik a = ucesnik(31L, "Ana");
        Ucesnik b = ucesnik(32L, "Bojan");
        Ucesnik c = ucesnik(33L, "Ceca");
        odgovor(trenutna(), a, 0, false).setTekst("Glupost");
        Odgovor sakriven = odgovor(trenutna(), b, 0, false);
        sakriven.setTekst("glupost ");
        sakriven.setSakriven(true);
        odgovor(trenutna(), c, 1000, true).setTekst("Njutn");

        Rezultat r = service.javno(IZ).rezultat();

        assertEquals(1, r.ukupno());
        assertEquals(List.of("njutn"), r.tekstovi().stream().map(RezultatTekst::kljuc).toList());
        assertNull(r.tekstovi().get(0).tacan());
        assertFalse(jsonMapper.writeValueAsString(r).toLowerCase(Locale.ROOT).contains("glupost"));

        iz.setTacanPrikazan(true);
        assertEquals(Boolean.TRUE, service.javno(IZ).rezultat().tekstovi().get(0).tacan());
    }

    @Test
    void brojUOdstupanjuTekPosleTacan() {
        // "u odstupanju x/y" govori koliko je tačnih: u javnom rezultatu tek kad je tačan odgovor prikazan
        na(TipPitanja.BROJ, Faza.ZATVORENO);
        iz.setRezultatiPrikazani(true);
        odgovor(trenutna(), ucesnik(31L, "Ana"), 1000, true).setBroj(9.8);
        odgovor(trenutna(), ucesnik(32L, "Bojan"), 0, false).setBroj(3.0);

        RezultatBrojevi pre = service.javno(IZ).rezultat().brojevi();
        assertNull(pre.uOdstupanju());
        assertEquals((9.8 + 3.0) / 2, pre.medijana());
        assertEquals(2, pre.najcesce().size());
        // projektor isto (Space pre C), a konzola vidi broj tačnih uživo
        assertNull(service.nastavnicko(IZ).javniRezultat().brojevi().uOdstupanju());
        assertEquals(1, service.nastavnicko(IZ).rezultat().brojevi().uOdstupanju());

        iz.setTacanPrikazan(true);
        assertEquals(1, service.javno(IZ).rezultat().brojevi().uOdstupanju());
        assertEquals(1, service.nastavnicko(IZ).javniRezultat().brojevi().uOdstupanju());
    }

    @Test
    void rezultatBezIzbacenih() {
        na(TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        iz.setRezultatiPrikazani(true);
        Ucesnik ana = ucesnik(31L, "Ana");
        Ucesnik izbacen = ucesnik(32L, "Bojan");
        izbacen.setIzbacen(true);
        odgovor(trenutna(), ana, 1000, true).setOpcije("121");
        odgovor(trenutna(), izbacen, 0, false).setOpcije("122");

        Rezultat r = service.javno(IZ).rezultat();
        assertEquals(1, r.ukupno());
        assertEquals(0, r.opcije().get(1).broj());
    }

    @Test
    void rezultatUzivoDokJeOtvoreno() {
        na(TipPitanja.ANKETA, Faza.OTVORENO);
        iz.setRezultatiPrikazani(true);
        odgovor(trenutna(), ucesnik(31L, "Ana"), 0, null).setOpcije("142");
        assertEquals(1, service.javno(IZ).rezultat().opcije().get(1).broj());
    }

    // ---------------------------------------------------------------- rang-lista

    /** Sedam učesnika sa 700, 600, ... 100 poena (Ana prva); Izbačeni ima 5000. */
    void sedamUcesnika() {
        Slajd s = na(TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        String[] imena = {"Ana", "Bojan", "Ceca", "Dule", "Ema", "Filip", "Gaga"};
        for (int i = 0; i < imena.length; i++) {
            odgovor(trenutna(), ucesnik(31L + i, imena[i]), 700 - 100 * i, true);
        }
        Ucesnik izbacen = ucesnik(99L, "Izbačeni");
        izbacen.setIzbacen(true);
        odgovor(trenutna(), izbacen, 5000, true);
        assertNotNull(s);
    }

    @Test
    void rangListaNikadSamaOdSebe() {
        sedamUcesnika();
        assertNull(service.javno(IZ).rangLista());
        iz.setPrikaz(Prikaz.KRAJ);
        iz.setTakmicenje(false);
        assertNull(service.javno(IZ).rangLista());
    }

    @Test
    void rangListaTop5BezIdJeva() {
        sedamUcesnika();
        iz.setRangListaPrikazana(true);
        iz.setTacanPrikazan(true);   // poeni trenutne runde se vide tek posle TACAN

        List<RangStavka> rang = service.javno(IZ).rangLista();

        assertEquals(List.of(new RangStavka(1, null, "Ana", 700), new RangStavka(2, null, "Bojan", 600),
                new RangStavka(3, null, "Ceca", 500), new RangStavka(4, null, "Dule", 400),
                new RangStavka(5, null, "Ema", 300)), rang);
        assertFalse(jsonMapper.writeValueAsString(service.javno(IZ)).contains("\"ucesnikId\":3"));
    }

    @Test
    void rangListaNaKrajuSaTakmicenjem() {
        sedamUcesnika();
        iz.setPrikaz(Prikaz.KRAJ);
        iz.setTrenutniSlajdId(null);
        iz.setFaza(null);
        iz.setTrenutnaRundaId(null);
        JavnoStanje st = service.javno(IZ);
        // na kraju nema trenutne runde: računa se sve, i poslednje pitanje bez TACAN
        assertEquals(new RangStavka(1, null, "Ana", 700), st.rangLista().get(0));
        assertEquals(5, st.rangLista().size());
        assertNull(st.pitanje());
        assertNull(st.slajdTip());
        // postolje na projektoru: ista lista
        projektorKaoJavno(st);
    }

    @Test
    void rangSamoIzPoslednjeRundeSlajda() {
        // runda 201 (stara) daje Bojanu 900; posle PONOVI važi samo runda 202
        Slajd s = na(TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        trenutna().setRedniBroj(2);
        PitanjeRunda stara = runda(201L, s, 1);
        Ucesnik ana = ucesnik(31L, "Ana");
        Ucesnik bojan = ucesnik(32L, "Bojan");
        odgovor(stara, bojan, 900, true);
        odgovor(trenutna(), ana, 100, true);
        iz.setRangListaPrikazana(true);

        // pre TACAN: stara runda više ne važi, a nova se još ne vidi
        assertEquals(List.of(new RangStavka(1, null, "Ana", 0), new RangStavka(2, null, "Bojan", 0)),
                service.javno(IZ).rangLista());
        iz.setTacanPrikazan(true);
        assertEquals(List.of(new RangStavka(1, null, "Ana", 100), new RangStavka(2, null, "Bojan", 0)),
                service.javno(IZ).rangLista());
        assertEquals(0, service.licno(IZ, 32L).poeni());
    }

    // ---------------------------------------------------------------- lično stanje

    @Test
    void licnoPoeniMestoIOdgovor() {
        sedamUcesnika();
        // Ceca je odgovorila u trenutnoj rundi tačno (500), Hana nije odgovarala
        ucesnik(40L, "Hana");

        // pre TACAN poeni trenutne runde se ne vide (skok poena bi odao tačnost): svi 0, mesto po prijavi
        LicnoStanje ceca = service.licno(IZ, 33L);
        assertEquals(17, ceca.verzija());
        assertEquals(33L, ceca.ucesnikId());
        assertEquals("Ceca", ceca.ime());
        assertEquals(0, ceca.poeni());
        assertEquals(3, ceca.mesto());
        assertEquals(8, ceca.brojUcesnika());
        assertFalse(ceca.izbacen());
        assertEquals(new LicniOdgovor(RUNDA, true, null, null), ceca.odgovor());

        LicnoStanje hana = service.licno(IZ, 40L);
        assertEquals(0, hana.poeni());
        assertEquals(8, hana.mesto());
        assertEquals(new LicniOdgovor(RUNDA, false, null, null), hana.odgovor());

        iz.setTacanPrikazan(true);
        ceca = service.licno(IZ, 33L);
        assertEquals(500, ceca.poeni());
        assertEquals(3, ceca.mesto());
        assertEquals(new LicniOdgovor(RUNDA, true, true, 500), ceca.odgovor());
        assertEquals(0, service.licno(IZ, 40L).poeni());
        assertEquals(new LicniOdgovor(RUNDA, false, null, null), service.licno(IZ, 40L).odgovor());
    }

    @Test
    void licnoNetacanPosleTacan() {
        na(TipPitanja.JEDAN_TACAN, Faza.ZATVORENO);
        odgovor(trenutna(), ucesnik(31L, "Ana"), 0, false);
        iz.setTacanPrikazan(true);
        assertEquals(new LicniOdgovor(RUNDA, true, false, 0), service.licno(IZ, 31L).odgovor());
    }

    @Test
    void licnoBezTakmicenjaNemaMesta() {
        sedamUcesnika();
        iz.setTakmicenje(false);
        assertNull(service.licno(IZ, 31L).mesto());
    }

    @Test
    void licnoPrimljenOdmahDokJeOtvoreno() {
        na(TipPitanja.JEDAN_TACAN, Faza.OTVORENO);
        odgovor(trenutna(), ucesnik(31L, "Ana"), 750, true);
        ucesnik(32L, "Bojan");
        assertEquals(new LicniOdgovor(RUNDA, true, null, null), service.licno(IZ, 31L).odgovor());
        assertEquals(new LicniOdgovor(RUNDA, false, null, null), service.licno(IZ, 32L).odgovor());
    }

    @Test
    void licnoBezRundeNemaOdgovora() {
        ucesnik(31L, "Ana");
        assertNull(service.licno(IZ, 31L).odgovor());
        na(TipPitanja.JEDAN_TACAN, Faza.CEKA);
        assertNull(service.licno(IZ, 31L).odgovor());
    }

    @Test
    void licnoIzbacenog() {
        sedamUcesnika();
        LicnoStanje l = service.licno(IZ, 99L);
        assertTrue(l.izbacen());
        assertEquals(99L, l.ucesnikId());
        assertEquals(0, l.poeni());
        assertNull(l.mesto());
        assertNull(l.odgovor());
        assertEquals(7, l.brojUcesnika());
    }

    @Test
    void licnoNepoznatogJe404() {
        SystemException e = assertThrows(SystemException.class, () -> service.licno(IZ, 12345L));
        assertEquals(404, e.getCode());
    }

    @Test
    void licnaZaSveRacunaJednom() {
        sedamUcesnika();
        iz.setTacanPrikazan(true);

        Map<Long, LicnoStanje> sva = service.licnaZaSve(IZ);

        assertEquals(7, sva.size());
        assertFalse(sva.containsKey(99L));
        for (int i = 0; i < 7; i++) {
            LicnoStanje l = sva.get(31L + i);
            assertEquals(i + 1, l.mesto());
            assertEquals(700 - 100 * i, l.poeni());
            assertEquals(new LicniOdgovor(RUNDA, true, true, 700 - 100 * i), l.odgovor());
            assertEquals(service.licno(IZ, 31L + i), l);
        }
        // jedno učitavanje za sve (i još 7 za pojedinačne provere iznad)
        verify(odgovorRepository, times(1 + 7)).findAllByRundaIzvodjenjeId(IZ);
    }

    @Test
    void licnaZaSveJedanUpitOdgovora() {
        sedamUcesnika();
        service.licnaZaSve(IZ);
        verify(odgovorRepository, times(1)).findAllByRundaIzvodjenjeId(IZ);
        verify(izvodjenjeRepository, times(1)).findSaVezama(IZ);
    }

    @Test
    void pocetnoJednimUcitavanjem() {
        sedamUcesnika();
        iz.setRangListaPrikazana(true);

        PocetnoStanje p = service.pocetno(IZ, 32L);

        assertEquals(service.javno(IZ), p.javno());
        assertEquals(service.licno(IZ, 32L), p.licno());
        verify(odgovorRepository, times(3)).findAllByRundaIzvodjenjeId(IZ);
        verify(izvodjenjeRepository, times(3)).findSaVezama(IZ);
    }

    @Test
    void zavrsenoBezCuvanja() {
        // ZAVRSI bez čuvanja: runde, odgovori i učesnici obrisani, faza i runda prazne
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        iz.setAktivanKod(null);
        iz.setPrikaz(Prikaz.SLAJD);
        iz.setTrenutniSlajdId(pitanja.get(TipPitanja.JEDAN_TACAN).getId());
        iz.setFaza(null);
        iz.setTrenutnaRundaId(null);
        JavnoStanje st = service.javno(IZ);
        assertEquals(StatusIzvodjenja.ZAVRSENO, st.status());
        samoTipIFaza(st.pitanje(), TipPitanja.JEDAN_TACAN, Faza.CEKA);
        bezTajni(st);
    }
}
