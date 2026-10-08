package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.uzivo.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tok izvođenja bez baze: prezentacija S1 INFO postepeno ("- a\n- b"), S2 JEDAN_TACAN sa 20 s, S3 ANKETA bez vremena.
 * Runde žive u mapi iza mock repozitorijuma; sat pomera test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IzvodjenjeServiceTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 7, 12, 0);
    static final Long IZ = 5L;

    @Mock IzvodjenjeRepository izvodjenjeRepository;
    @Mock PrezentacijaRepository prezentacijaRepository;
    @Mock SlajdRepository slajdRepository;
    @Mock PitanjeRundaRepository rundaRepository;
    @Mock OdgovorRepository odgovorRepository;
    @Mock UcesnikRepository ucesnikRepository;
    @Mock PredavanjeRepository predavanjeRepository;
    @Mock GrupaRepository grupaRepository;
    @Mock KodGenerator kodGenerator;
    @Mock RokPlaner rokPlaner;
    @Mock StanjeService stanjeService;
    @Mock ImenaUcesnika imenaUcesnika;
    @Mock ApplicationEventPublisher publisher;

    final MutableClock clock = new MutableClock(T0.atZone(ZONA).toInstant(), ZONA);
    final JsonMapper jsonMapper = JsonMapper.builder().build();
    final Map<Long, PitanjeRunda> runde = new LinkedHashMap<>();
    final AtomicLong rundaId = new AtomicLong(100);

    IzvodjenjeService service;
    Predmet predmet;
    Prezentacija prez;
    Slajd s1;
    Slajd s2;
    Slajd s3;
    List<Slajd> slajdovi;
    Izvodjenje iz;

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setId(7L);
        predmet.setNaziv("Statika");
        prez = new Prezentacija();
        prez.setId(1L);
        prez.setPredmet(predmet);
        prez.setNaziv("Uvod");
        prez.setTakmicenje(true);
        prez.setTelefonPrikaz(TelefonPrikaz.PITANJE);
        prez.setDetaljiDozvoljeni(false);

        s1 = new Slajd();
        s1.setId(11L);
        s1.setRb(1);
        s1.setTip(TipSlajda.INFO);
        s1.setSadrzaj("- a\n- b");
        s1.setPostepeno(true);
        s1.setPrezentacija(prez);
        s2 = pitanje(12L, 2, TipPitanja.JEDAN_TACAN, 20);
        s3 = pitanje(13L, 3, TipPitanja.ANKETA, null);
        slajdovi = new ArrayList<>(List.of(s1, s2, s3));

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
        iz.setDetaljiDozvoljeni(true);
        iz.setTakmicenje(true);
        iz.setCuvanje(true);
        iz.setVerzija(1);

        when(slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(1L)).thenAnswer(i -> new ArrayList<>(slajdovi));
        when(izvodjenjeRepository.findByIdForUpdate(IZ)).thenReturn(Optional.of(iz));
        when(izvodjenjeRepository.findIdsByPrezentacijaIdAndStatus(1L, StatusIzvodjenja.AKTIVNO))
                .thenReturn(List.of(IZ));
        when(rundaRepository.save(any(PitanjeRunda.class))).thenAnswer(i -> {
            PitanjeRunda r = i.getArgument(0);
            if (r.getId() == null) r.setId(rundaId.incrementAndGet());
            runde.put(r.getId(), r);
            return r;
        });
        when(rundaRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(runde.get((Long) i.getArgument(0))));
        when(rundaRepository.findByIdAndIzvodjenjeId(anyLong(), eq(IZ)))
                .thenAnswer(i -> Optional.ofNullable(runde.get((Long) i.getArgument(0))));
        when(rundaRepository.findFirstByIzvodjenjeIdAndSlajdIdOrderByRedniBrojDesc(eq(IZ), anyLong())).thenAnswer(i -> {
            Long slajdId = i.getArgument(1);
            return runde.values().stream().filter(r -> slajdId.equals(r.getSlajdId()))
                    .max(Comparator.comparingInt(PitanjeRunda::getRedniBroj));
        });

        service = new IzvodjenjeService(izvodjenjeRepository, prezentacijaRepository, slajdRepository, rundaRepository,
                odgovorRepository, ucesnikRepository, predavanjeRepository, grupaRepository, kodGenerator, rokPlaner,
                stanjeService, imenaUcesnika, publisher, jsonMapper, clock);
    }

    Slajd pitanje(Long id, int rb, TipPitanja tip, Integer sekunde) {
        Pitanje p = new Pitanje();
        p.setId(id * 10);
        p.setTip(tip);
        p.setTekst("Pitanje " + id);
        p.setVremeSekunde(sekunde);
        for (int i = 0; i < 2; i++) {
            PitanjeOpcija o = new PitanjeOpcija();
            o.setId(id * 10 + i + 1);
            o.setRb(i + 1);
            o.setTekst("Opcija " + (i + 1));
            o.setTacna(tip != TipPitanja.ANKETA && i == 0);
            o.setPitanje(p);
            p.getOpcije().add(o);
        }
        Slajd s = new Slajd();
        s.setId(id);
        s.setRb(rb);
        s.setTip(TipSlajda.PITANJE);
        s.setPitanje(p);
        s.setPrezentacija(prez);
        return s;
    }

    void k(TipKomande tip) {
        service.komanda(IZ, new KomandaCmd(tip, null));
    }

    void idiNa(int indeks) {
        service.komanda(IZ, new KomandaCmd(TipKomande.IDI_NA, indeks));
    }

    SystemException greska(TipKomande tip) {
        return assertThrows(SystemException.class, () -> k(tip));
    }

    void assertGreska(int kod, String poruka, TipKomande tip) {
        SystemException e = greska(tip);
        assertEquals(kod, e.getCode(), poruka);
        assertEquals(poruka, e.getMessage());
    }

    void stanje(Prikaz prikaz, Long slajdId, int korak, Faza faza) {
        assertEquals(prikaz, iz.getPrikaz(), "prikaz");
        assertEquals(slajdId, iz.getTrenutniSlajdId(), "slajd");
        assertEquals(korak, iz.getKorak(), "korak");
        assertEquals(faza, iz.getFaza(), "faza");
    }

    PitanjeRunda runda() {
        return runde.get(iz.getTrenutnaRundaId());
    }

    LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }

    /** Dovodi izvođenje na S2 sa zatvorenom prvom rundom. */
    void doS2Zatvoreno() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.ZATVORENO);
    }

    // ---------------------------------------------------------------- 1. pokretanje

    @Test
    void pokreniBezPitanjaNeCuva() {
        when(prezentacijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(prez));
        when(slajdRepository.countByPrezentacijaIdAndTip(1L, TipSlajda.PITANJE)).thenReturn(0L);
        when(kodGenerator.novi()).thenReturn("042424");
        when(izvodjenjeRepository.saveAndFlush(any())).thenAnswer(i -> {
            Izvodjenje novo = i.getArgument(0);
            novo.setId(9L);
            return novo;
        });

        IzvodjenjeInfo info = service.pokreni(1L, new PokreniCmd(true, null, null));

        ArgumentCaptor<Izvodjenje> c = ArgumentCaptor.forClass(Izvodjenje.class);
        verify(izvodjenjeRepository).saveAndFlush(c.capture());
        Izvodjenje novo = c.getValue();
        assertFalse(novo.isCuvanje());
        assertEquals(9L, info.id());
        assertFalse(info.cuvanje());
        assertEquals("042424", info.kod());
        assertEquals(new PrezentacijaKratko(1L, "Uvod", 7L), info.prezentacija());
        assertNull(info.grupa());
        assertNull(info.predavanje());
        // zaključava prezentaciju pre upisa (trka sa brisanjem prezentacije)
        InOrder red = inOrder(prezentacijaRepository, izvodjenjeRepository);
        red.verify(prezentacijaRepository).findByIdForUpdate(1L);
        red.verify(izvodjenjeRepository).saveAndFlush(any());
    }

    @Test
    void pokreniPocetnoStanje() {
        when(prezentacijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(prez));
        when(slajdRepository.countByPrezentacijaIdAndTip(1L, TipSlajda.PITANJE)).thenReturn(2L);
        when(kodGenerator.novi()).thenReturn("123456");
        when(izvodjenjeRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Grupa g = new Grupa();
        g.setId(3L);
        g.setNaziv("GD-2025");
        when(grupaRepository.findById(3L)).thenReturn(Optional.of(g));

        service.pokreni(1L, new PokreniCmd(true, 3L, null));

        ArgumentCaptor<Izvodjenje> c = ArgumentCaptor.forClass(Izvodjenje.class);
        verify(izvodjenjeRepository).saveAndFlush(c.capture());
        Izvodjenje n = c.getValue();
        assertTrue(n.isCuvanje());
        assertSame(g, n.getGrupa());
        assertNull(n.getPredavanje());
        assertSame(prez, n.getPrezentacija());
        assertEquals(StatusIzvodjenja.AKTIVNO, n.getStatus());
        assertEquals(Prikaz.PRIJAVA, n.getPrikaz());
        assertEquals(1, n.getVerzija());
        assertEquals("123456", n.getKod());
        assertEquals("123456", n.getAktivanKod());
        assertEquals(T0, n.getPocetak());
        assertNull(n.getKraj());
        assertNull(n.getTrenutniSlajdId());
        assertNull(n.getFaza());
        assertEquals(0, n.getKorak());
        assertEquals(Ekran.NORMALAN, n.getEkran());
        assertFalse(n.isQrPrikazan());
        // podešavanja kopirana iz prezentacije
        assertTrue(n.isTakmicenje());
        assertEquals(TelefonPrikaz.PITANJE, n.getTelefonPrikaz());
        assertFalse(n.isDetaljiDozvoljeni());
    }

    @Test
    void pokreniSaPredavanjem() {
        when(prezentacijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(prez));
        when(slajdRepository.countByPrezentacijaIdAndTip(1L, TipSlajda.PITANJE)).thenReturn(2L);
        when(kodGenerator.novi()).thenReturn("123456");
        when(izvodjenjeRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Grupa g = new Grupa();
        g.setId(3L);
        g.setNaziv("GD-2025");
        Predavanje pr = new Predavanje();
        pr.setId(40L);
        pr.setPredmet(predmet);
        pr.setGrupa(g);
        pr.setRb(4);
        pr.setDatum(LocalDate.of(2026, 10, 7));
        pr.setTema("Sile");
        when(predavanjeRepository.findById(40L)).thenReturn(Optional.of(pr));

        IzvodjenjeInfo info = service.pokreni(1L, new PokreniCmd(false, null, 40L));

        assertTrue(info.cuvanje());
        assertEquals(new GrupaKratko(3L, "GD-2025"), info.grupa());
        assertEquals(new PredavanjeKratko(40L, 4, LocalDate.of(2026, 10, 7), "Sile"), info.predavanje());
    }

    @Test
    void pokreniSaPredavanjemDrugogPredmeta400() {
        when(prezentacijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(prez));
        when(slajdRepository.countByPrezentacijaIdAndTip(1L, TipSlajda.PITANJE)).thenReturn(2L);
        Predmet drugi = new Predmet();
        drugi.setId(8L);
        Predavanje pr = new Predavanje();
        pr.setId(40L);
        pr.setPredmet(drugi);
        when(predavanjeRepository.findById(40L)).thenReturn(Optional.of(pr));

        SystemException e = assertThrows(SystemException.class,
                () -> service.pokreni(1L, new PokreniCmd(true, null, 40L)));
        assertEquals(400, e.getCode());
        assertEquals("Predavanje nije iz predmeta ove prezentacije.", e.getMessage());
        verify(izvodjenjeRepository, never()).saveAndFlush(any());
    }

    @Test
    void pokreniDokTrajeDrugoIzvodjenje409() {
        when(prezentacijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(prez));
        when(izvodjenjeRepository.existsByPrezentacijaIdAndStatus(1L, StatusIzvodjenja.AKTIVNO)).thenReturn(true);
        SystemException e = assertThrows(SystemException.class,
                () -> service.pokreni(1L, new PokreniCmd(true, null, null)));
        assertEquals(409, e.getCode());
        assertEquals("Prezentacija već ima izvođenje u toku.", e.getMessage());
        // provera je pod zaključanom prezentacijom (dva istovremena pokretanja idu redom)
        InOrder red = inOrder(prezentacijaRepository, izvodjenjeRepository);
        red.verify(prezentacijaRepository).findByIdForUpdate(1L);
        red.verify(izvodjenjeRepository).existsByPrezentacijaIdAndStatus(1L, StatusIzvodjenja.AKTIVNO);
        verify(izvodjenjeRepository, never()).saveAndFlush(any());
        verifyNoInteractions(kodGenerator);
    }

    void pokreniKadUpisPadne(RuntimeException greska) {
        when(prezentacijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(prez));
        when(slajdRepository.countByPrezentacijaIdAndTip(1L, TipSlajda.PITANJE)).thenReturn(2L);
        when(kodGenerator.novi()).thenReturn("123456");
        when(izvodjenjeRepository.saveAndFlush(any())).thenThrow(greska);
    }

    @Test
    void zauzetAktivanKodJe503() {
        pokreniKadUpisPadne(new DataIntegrityViolationException("upis", new java.sql.SQLIntegrityConstraintViolationException(
                "Duplicate entry '123456' for key 'izvodjenja.uk_izvodjenja_aktivan_kod'")));
        SystemException e = assertThrows(SystemException.class,
                () -> service.pokreni(1L, new PokreniCmd(true, null, null)));
        assertEquals(503, e.getCode());
        assertEquals("Trenutno nema slobodnog koda, pokušaj ponovo.", e.getMessage());
    }

    @Test
    void drugaGreskaIntegritetaSeProsledjuje() {
        DataIntegrityViolationException greska = new DataIntegrityViolationException("upis",
                new java.sql.SQLIntegrityConstraintViolationException(
                        "Cannot add or update a child row: a foreign key constraint fails (`gftest`.`izvodjenja`, "
                                + "CONSTRAINT `fk_izvodjenja_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`))"));
        pokreniKadUpisPadne(greska);
        assertSame(greska, assertThrows(DataIntegrityViolationException.class,
                () -> service.pokreni(1L, new PokreniCmd(true, null, null))));
    }

    @Test
    void pokreniNepostojecePrezentacije404() {
        when(prezentacijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        SystemException e = assertThrows(SystemException.class,
                () -> service.pokreni(1L, new PokreniCmd(false, null, null)));
        assertEquals(404, e.getCode());
    }

    // ---------------------------------------------------------------- 2. niz SLEDECI

    @Test
    void sledeciKrozCeluPrezentaciju() {
        long v = iz.getVerzija();
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 11L, 0, null);
        assertEquals(++v, iz.getVerzija());
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 11L, 1, null);
        assertEquals(++v, iz.getVerzija());
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 11L, 2, null);
        assertEquals(++v, iz.getVerzija());
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.CEKA);
        assertNull(iz.getTrenutnaRundaId());
        assertEquals(++v, iz.getVerzija());

        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.OTVORENO);
        PitanjeRunda r1 = runda();
        assertEquals(1, r1.getRedniBroj());
        assertEquals(12L, r1.getSlajdId());
        assertSame(iz, r1.getIzvodjenje());
        assertEquals(T0, r1.getOtvoreno());
        assertEquals(T0.plusSeconds(20), r1.getRok());
        assertEquals(20_000L, r1.getTrajanjeMs());
        assertNull(r1.getZatvoreno());
        PitanjeSnimak snimak = jsonMapper.readValue(r1.getSnimak(), PitanjeSnimak.class);
        assertEquals(PitanjeSnimak.od(s2), snimak);
        verify(rokPlaner).zakazi(IZ, r1.getId(), T0.plusSeconds(20));
        assertEquals(++v, iz.getVerzija());

        clock.pomeri(Duration.ofSeconds(7));
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.ZATVORENO);
        assertEquals(T0.plusSeconds(7), r1.getZatvoreno());
        verify(rokPlaner).otkazi(r1.getId());
        assertEquals(++v, iz.getVerzija());

        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 13L, 0, Faza.CEKA);
        assertNull(iz.getTrenutnaRundaId());
        assertEquals(++v, iz.getVerzija());

        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 13L, 0, Faza.OTVORENO);
        PitanjeRunda r2 = runda();
        assertNotEquals(r1.getId(), r2.getId());
        assertEquals(1, r2.getRedniBroj());
        assertNull(r2.getRok());
        assertNull(r2.getTrajanjeMs());
        verify(rokPlaner, never()).zakazi(eq(IZ), eq(r2.getId()), any());
        assertEquals(++v, iz.getVerzija());

        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 13L, 0, Faza.ZATVORENO);
        assertEquals(++v, iz.getVerzija());

        k(TipKomande.SLEDECI);
        stanje(Prikaz.KRAJ, null, 0, null);
        assertNull(iz.getTrenutnaRundaId());
        assertEquals(++v, iz.getVerzija());

        k(TipKomande.SLEDECI);
        stanje(Prikaz.KRAJ, null, 0, null);
        assertEquals(++v, iz.getVerzija());
        assertEquals(2, runde.size());
    }

    @Test
    void praznaPrezentacijaIdeNaKraj() {
        slajdovi.clear();
        k(TipKomande.SLEDECI);
        stanje(Prikaz.KRAJ, null, 0, null);
        k(TipKomande.PRETHODNI);
        stanje(Prikaz.PRIJAVA, null, 0, null);
    }

    // ---------------------------------------------------------------- 3. PRETHODNI

    @Test
    void prethodniUnazad() {
        doS2Zatvoreno();
        Long r1 = iz.getTrenutnaRundaId();
        k(TipKomande.SLEDECI);
        k(TipKomande.SLEDECI);
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 13L, 0, Faza.ZATVORENO);

        k(TipKomande.PRETHODNI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.ZATVORENO);
        assertEquals(r1, iz.getTrenutnaRundaId(), "poslednja runda tog slajda");

        k(TipKomande.PRETHODNI);
        stanje(Prikaz.SLAJD, 11L, 2, null);
        assertNull(iz.getTrenutnaRundaId());
        k(TipKomande.PRETHODNI);
        stanje(Prikaz.SLAJD, 11L, 1, null);
        k(TipKomande.PRETHODNI);
        stanje(Prikaz.SLAJD, 11L, 0, null);
        k(TipKomande.PRETHODNI);
        stanje(Prikaz.PRIJAVA, null, 0, null);
        long v = iz.getVerzija();
        k(TipKomande.PRETHODNI);
        stanje(Prikaz.PRIJAVA, null, 0, null);
        assertEquals(v + 1, iz.getVerzija());
    }

    @Test
    void prethodniSaKrajaNaPoslednjiSlajd() {
        idiNa(3);
        stanje(Prikaz.KRAJ, null, 0, null);
        k(TipKomande.PRETHODNI);
        stanje(Prikaz.SLAJD, 13L, 0, Faza.CEKA);
    }

    @Test
    void prethodniDokJeOtvorenoPrvoZatvara() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.OTVORENO);
        PitanjeRunda r = runda();
        k(TipKomande.PRETHODNI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.ZATVORENO);
        assertNotNull(r.getZatvoreno());
        verify(rokPlaner).otkazi(r.getId());
    }

    @Test
    void idiNaSaOtvorenimPitanjemZatvaraRundu() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        idiNa(0);
        stanje(Prikaz.SLAJD, 11L, 0, null);
        assertNotNull(r.getZatvoreno());
        verify(rokPlaner).otkazi(r.getId());
        idiNa(-1);
        stanje(Prikaz.PRIJAVA, null, 0, null);
        idiNa(3);
        stanje(Prikaz.KRAJ, null, 0, null);
    }

    // ---------------------------------------------------------------- 4. promena slajda gasi prikaze

    @Test
    void promenaSlajdaGasiRezultateTacanIRangListu() {
        doS2Zatvoreno();
        iz.setRezultatiPrikazani(true);
        iz.setTacanPrikazan(true);
        iz.setRangListaPrikazana(true);
        iz.setEkran(Ekran.CRN);
        iz.setQrPrikazan(true);
        k(TipKomande.SLEDECI);
        assertFalse(iz.isRezultatiPrikazani());
        assertFalse(iz.isTacanPrikazan());
        assertFalse(iz.isRangListaPrikazana());
        assertEquals(Ekran.CRN, iz.getEkran());
        assertTrue(iz.isQrPrikazan());
    }

    @Test
    void korakNaInfoSlajduNeGasiPrikaze() {
        idiNa(0);
        iz.setRangListaPrikazana(true);
        k(TipKomande.SLEDECI);
        assertEquals(1, iz.getKorak());
        assertTrue(iz.isRangListaPrikazana());
    }

    // ---------------------------------------------------------------- 5. zabranjene komande

    @Test
    void zabranjeneKomande() {
        // PRIJAVA
        assertGreska(409, "Nema rezultata za prikaz.", TipKomande.REZULTATI);
        assertGreska(409, "Tajmer radi samo dok je pitanje otvoreno.", TipKomande.TAJMER);
        assertGreska(409, "Tajmer radi samo dok je pitanje otvoreno.", TipKomande.TAJMER_PLUS);
        assertGreska(409, "Tajmer radi samo dok je pitanje otvoreno.", TipKomande.TAJMER_MINUS);

        // S2 CEKA
        idiNa(1);
        assertGreska(409, "Nema rezultata za prikaz.", TipKomande.REZULTATI);
        assertGreska(409, "Pitanje još nije postavljeno.", TipKomande.PONOVI);
        assertGreska(409, "Tajmer radi samo dok je pitanje otvoreno.", TipKomande.TAJMER);

        // S2 OTVORENO
        k(TipKomande.SLEDECI);
        assertGreska(409, "Prvo zatvori pitanje.", TipKomande.TACAN);

        // S3 ANKETA (zatvorena)
        k(TipKomande.SLEDECI);
        k(TipKomande.SLEDECI);
        k(TipKomande.SLEDECI);
        k(TipKomande.SLEDECI);
        stanje(Prikaz.SLAJD, 13L, 0, Faza.ZATVORENO);
        assertGreska(409, "Ovo pitanje nema tačan odgovor.", TipKomande.TACAN);
        assertGreska(409, "Tajmer radi samo dok je pitanje otvoreno.", TipKomande.TAJMER);

        // bez takmičenja
        iz.setTakmicenje(false);
        assertGreska(409, "Takmičenje nije uključeno.", TipKomande.RANG_LISTA);

        // nepostojeći slajd
        for (Integer indeks : new Integer[]{99, 4, -2, null}) {
            SystemException e = assertThrows(SystemException.class,
                    () -> service.komanda(IZ, new KomandaCmd(TipKomande.IDI_NA, indeks)));
            assertEquals(400, e.getCode());
            assertEquals("Nepostojeći slajd.", e.getMessage());
        }

        // INFO slajd: pitanje i rezultat ne postoje
        idiNa(0);
        assertGreska(409, "Na ovom slajdu nema pitanja.", TipKomande.OTVORI_ZATVORI);
        assertGreska(409, "Na ovom slajdu nema pitanja.", TipKomande.TACAN);
        assertGreska(409, "Na ovom slajdu nema pitanja.", TipKomande.PONOVI);
        assertGreska(409, "Nema rezultata za prikaz.", TipKomande.REZULTATI);
    }

    @Test
    void neispravnaKomanda400() {
        SystemException e = assertThrows(SystemException.class, () -> service.komanda(IZ, new KomandaCmd(null, null)));
        assertEquals(400, e.getCode());
        e = assertThrows(SystemException.class, () -> service.komanda(IZ, null));
        assertEquals(400, e.getCode());
    }

    @Test
    void zavrsenoIzvodjenje410() {
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        long v = iz.getVerzija();
        for (TipKomande t : TipKomande.values()) {
            assertGreska(410, "Izvođenje je završeno.", t);
        }
        assertEquals(v, iz.getVerzija());
        verifyNoInteractions(publisher);
    }

    @Test
    void nepostojeceIzvodjenje404() {
        when(izvodjenjeRepository.findByIdForUpdate(77L)).thenReturn(Optional.empty());
        SystemException e = assertThrows(SystemException.class,
                () -> service.komanda(77L, new KomandaCmd(TipKomande.SLEDECI, null)));
        assertEquals(404, e.getCode());
        assertEquals("Izvođenje nije pronađeno.", e.getMessage());
    }

    // ---------------------------------------------------------------- prikazi i prekidači

    @Test
    void prekidaci() {
        doS2Zatvoreno();
        k(TipKomande.REZULTATI);
        assertTrue(iz.isRezultatiPrikazani());
        k(TipKomande.REZULTATI);
        assertFalse(iz.isRezultatiPrikazani());
        k(TipKomande.TACAN);
        assertTrue(iz.isTacanPrikazan());
        k(TipKomande.TACAN);
        assertFalse(iz.isTacanPrikazan());
        k(TipKomande.RANG_LISTA);
        assertTrue(iz.isRangListaPrikazana());
        k(TipKomande.TELEFON_PRIKAZ);
        assertEquals(TelefonPrikaz.PITANJE, iz.getTelefonPrikaz());
        k(TipKomande.TELEFON_PRIKAZ);
        assertEquals(TelefonPrikaz.DUGMAD, iz.getTelefonPrikaz());
        k(TipKomande.DETALJI);
        assertFalse(iz.isDetaljiDozvoljeni());
        k(TipKomande.EKRAN_CRN);
        assertEquals(Ekran.CRN, iz.getEkran());
        k(TipKomande.EKRAN_BEO);
        assertEquals(Ekran.BEO, iz.getEkran());
        k(TipKomande.EKRAN_BEO);
        assertEquals(Ekran.NORMALAN, iz.getEkran());
        k(TipKomande.QR);
        assertTrue(iz.isQrPrikazan());
        k(TipKomande.QR);
        assertFalse(iz.isQrPrikazan());
    }

    @Test
    void rezultatiDokJeOtvorenoDozvoljeni() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        k(TipKomande.REZULTATI);
        assertTrue(iz.isRezultatiPrikazani());
    }

    // ---------------------------------------------------------------- 6. tajmer

    @Test
    void tajmerPauzaNastavakPlusMinus() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        assertEquals(T0.plusSeconds(20), r.getRok());

        clock.pomeri(Duration.ofSeconds(5));
        k(TipKomande.TAJMER);
        assertNull(r.getRok());
        assertEquals(15_000L, r.getPreostaloMs());
        verify(rokPlaner).otkazi(r.getId());

        clock.pomeri(Duration.ofSeconds(100));
        k(TipKomande.TAJMER);
        assertEquals(sada().plusSeconds(15), r.getRok());
        assertNull(r.getPreostaloMs());
        verify(rokPlaner).zakazi(IZ, r.getId(), sada().plusSeconds(15));

        k(TipKomande.TAJMER_PLUS);
        assertEquals(sada().plusSeconds(25), r.getRok());
        assertEquals(30_000L, r.getTrajanjeMs());
        verify(rokPlaner).zakazi(IZ, r.getId(), sada().plusSeconds(25));

        clock.pomeri(Duration.ofSeconds(17));   // preostalo 8 s
        k(TipKomande.TAJMER_MINUS);
        assertEquals(sada().plusSeconds(5), r.getRok(), "najmanje 5 s preostaje");
        assertEquals(27_000L, r.getTrajanjeMs());

        k(TipKomande.TAJMER_MINUS);
        assertEquals(sada().plusSeconds(5), r.getRok(), "ispod minimuma se ne skraćuje");
        assertEquals(27_000L, r.getTrajanjeMs());
    }

    @Test
    void tajmerPlusMinusDokJePauziran() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        clock.pomeri(Duration.ofSeconds(4));
        k(TipKomande.TAJMER);
        assertEquals(16_000L, r.getPreostaloMs());
        k(TipKomande.TAJMER_PLUS);
        assertEquals(26_000L, r.getPreostaloMs());
        assertNull(r.getRok());
        k(TipKomande.TAJMER_MINUS);
        k(TipKomande.TAJMER_MINUS);
        assertEquals(6_000L, r.getPreostaloMs());
        k(TipKomande.TAJMER_MINUS);
        assertEquals(5_000L, r.getPreostaloMs());
        assertNull(r.getRok());
    }

    @Test
    void tajmerBezVremenaPokrece30s() {
        idiNa(2);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        assertNull(r.getRok());
        clock.pomeri(Duration.ofSeconds(3));
        k(TipKomande.TAJMER);
        assertEquals(sada().plusSeconds(30), r.getRok());
        verify(rokPlaner).zakazi(IZ, r.getId(), sada().plusSeconds(30));
    }

    // ---------------------------------------------------------------- 7. zatvaranje po roku

    @Test
    void zatvoriPoRokuPosleRoka() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        long v = iz.getVerzija();
        clock.pomeri(Duration.ofSeconds(21));
        service.zatvoriPoRoku(IZ, r.getId());
        assertEquals(Faza.ZATVORENO, iz.getFaza());
        assertEquals(T0.plusSeconds(21), r.getZatvoreno());
        assertEquals(v + 1, iz.getVerzija());
        verify(publisher, atLeastOnce()).publishEvent(new IzvodjenjePromenjeno(IZ));

        // već zatvorena: ništa
        service.zatvoriPoRoku(IZ, r.getId());
        assertEquals(v + 1, iz.getVerzija());
    }

    @Test
    void zatvoriPoRokuNeTrenutnaRundaNista() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        long v = iz.getVerzija();
        clock.pomeri(Duration.ofSeconds(25));
        service.zatvoriPoRoku(IZ, 999L);
        assertEquals(Faza.OTVORENO, iz.getFaza());
        assertEquals(v, iz.getVerzija());

        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        service.zatvoriPoRoku(IZ, iz.getTrenutnaRundaId());
        assertEquals(v, iz.getVerzija());
    }

    @Test
    void zatvoriPoRokuNePreRokaPomerenogPlusom() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        k(TipKomande.TAJMER_PLUS);
        long v = iz.getVerzija();
        clock.pomeri(Duration.ofSeconds(21));
        service.zatvoriPoRoku(IZ, r.getId());
        assertEquals(Faza.OTVORENO, iz.getFaza());
        assertNull(r.getZatvoreno());
        assertEquals(v, iz.getVerzija());
    }

    @Test
    void zatvoriPoRokuPauziranaNeZatvara() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        k(TipKomande.TAJMER);
        long v = iz.getVerzija();
        clock.pomeri(Duration.ofSeconds(60));
        service.zatvoriPoRoku(IZ, r.getId());
        assertEquals(Faza.OTVORENO, iz.getFaza());
        assertEquals(v, iz.getVerzija());
    }

    // ---------------------------------------------------------------- 8. ponovi, otvori/zatvori

    @Test
    void ponoviOtvaraNovuRundu() {
        doS2Zatvoreno();
        PitanjeRunda r1 = runda();
        iz.setRezultatiPrikazani(true);
        iz.setTacanPrikazan(true);
        clock.pomeri(Duration.ofSeconds(30));
        k(TipKomande.PONOVI);
        PitanjeRunda r2 = runda();
        assertNotEquals(r1.getId(), r2.getId());
        assertEquals(2, r2.getRedniBroj());
        assertEquals(Faza.OTVORENO, iz.getFaza());
        assertFalse(iz.isRezultatiPrikazani());
        assertFalse(iz.isTacanPrikazan());
        assertEquals(sada().plusSeconds(20), r2.getRok());
        verify(rokPlaner).zakazi(IZ, r2.getId(), sada().plusSeconds(20));
        assertNotNull(r1.getZatvoreno());
    }

    @Test
    void ponoviDokJeOtvorenoZatvaraStaruRundu() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r1 = runda();
        k(TipKomande.PONOVI);
        assertNotNull(r1.getZatvoreno());
        assertEquals(2, runda().getRedniBroj());
        verify(rokPlaner).otkazi(r1.getId());
    }

    @Test
    void otvoriZatvoriPonovoOtvaraIstuRunduBezRoka() {
        idiNa(1);
        k(TipKomande.OTVORI_ZATVORI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.OTVORENO);
        PitanjeRunda r = runda();
        k(TipKomande.OTVORI_ZATVORI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.ZATVORENO);
        iz.setTacanPrikazan(true);
        k(TipKomande.OTVORI_ZATVORI);
        stanje(Prikaz.SLAJD, 12L, 0, Faza.OTVORENO);
        assertEquals(r.getId(), iz.getTrenutnaRundaId());
        assertNull(r.getZatvoreno());
        assertNull(r.getRok());
        assertNull(r.getPreostaloMs());
        assertFalse(iz.isTacanPrikazan(), "tačan odgovor se ne sme videti dok je pitanje otvoreno");
        assertEquals(1, runde.size());
    }

    // ---------------------------------------------------------------- 9. završetak

    @Test
    void zavrsiBezCuvanjaBriseOdgovoreRundeIUcesnike() {
        iz.setCuvanje(false);
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        clock.pomeri(Duration.ofMinutes(5));
        k(TipKomande.ZAVRSI);
        assertEquals(StatusIzvodjenja.ZAVRSENO, iz.getStatus());
        assertNull(iz.getAktivanKod());
        assertEquals("123456", iz.getKod());
        assertEquals(sada(), iz.getKraj());
        assertNull(iz.getTrenutnaRundaId());
        assertNull(iz.getFaza(), "bez rundi nema ni faze");
        verify(rokPlaner).otkazi(r.getId());
        InOrder red = inOrder(odgovorRepository, rundaRepository, ucesnikRepository);
        red.verify(odgovorRepository).deleteAllByIzvodjenjeId(IZ);
        red.verify(rundaRepository).deleteAllByIzvodjenjeId(IZ);
        red.verify(ucesnikRepository).deleteAllByIzvodjenjeId(IZ);
        verify(publisher, atLeastOnce()).publishEvent(new IzvodjenjePromenjeno(IZ));
    }

    @Test
    void zavrsiSaCuvanjemNeBriseNista() {
        doS2Zatvoreno();
        k(TipKomande.ZAVRSI);
        assertEquals(StatusIzvodjenja.ZAVRSENO, iz.getStatus());
        assertNull(iz.getAktivanKod());
        assertEquals(T0, iz.getKraj());
        assertNotNull(iz.getTrenutnaRundaId());
        assertEquals(Faza.ZATVORENO, iz.getFaza(), "sa čuvanjem faza ostaje");
        verify(odgovorRepository, never()).deleteAllByIzvodjenjeId(any());
        verify(rundaRepository, never()).deleteAllByIzvodjenjeId(any());
        verify(ucesnikRepository, never()).deleteAllByIzvodjenjeId(any());
    }

    @Test
    void zavrsiAutomatski() {
        long v = iz.getVerzija();
        service.zavrsiAutomatski(IZ);
        assertEquals(StatusIzvodjenja.ZAVRSENO, iz.getStatus());
        assertEquals(v + 1, iz.getVerzija());
        verify(publisher).publishEvent(new IzvodjenjePromenjeno(IZ));
        // već završeno: ništa
        service.zavrsiAutomatski(IZ);
        assertEquals(v + 1, iz.getVerzija());
    }

    // ---------------------------------------------------------------- 10. izmene prezentacije tokom izvođenja

    @Test
    void obrisanTrenutniSlajdPrelaziNaSledeci() {
        UzivoPrezentacijaPromene promene = new UzivoPrezentacijaPromene(service);
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        iz.setRezultatiPrikazani(true);
        long v = iz.getVerzija();

        slajdovi.remove(s2);
        s3.setRb(2);
        promene.slajdObrisan(1L, 12L, 1);

        stanje(Prikaz.SLAJD, 13L, 0, Faza.CEKA);
        assertNull(iz.getTrenutnaRundaId());
        assertFalse(iz.isRezultatiPrikazani());
        assertNotNull(r.getZatvoreno(), "otvorena runda obrisanog slajda se zatvara");
        verify(rokPlaner).otkazi(r.getId());
        assertEquals(v + 1, iz.getVerzija());
        verify(publisher, atLeastOnce()).publishEvent(new IzvodjenjePromenjeno(IZ));
    }

    @Test
    void obrisanPoslednjiTrenutniSlajdIdeNaKraj() {
        UzivoPrezentacijaPromene promene = new UzivoPrezentacijaPromene(service);
        idiNa(2);
        slajdovi.remove(s3);
        promene.slajdObrisan(1L, 13L, 2);
        stanje(Prikaz.KRAJ, null, 0, null);
    }

    @Test
    void obrisanDrugiSlajdSamoVerzija() {
        UzivoPrezentacijaPromene promene = new UzivoPrezentacijaPromene(service);
        idiNa(2);
        long v = iz.getVerzija();
        slajdovi.remove(s1);
        promene.slajdObrisan(1L, 11L, 0);
        stanje(Prikaz.SLAJD, 13L, 0, Faza.CEKA);
        assertEquals(v + 1, iz.getVerzija());
    }

    @Test
    void slajdoviPromenjeniPovecavaVerzijuIObjavljuje() {
        UzivoPrezentacijaPromene promene = new UzivoPrezentacijaPromene(service);
        idiNa(0);
        long v = iz.getVerzija();
        clearInvocations(publisher, izvodjenjeRepository);
        promene.slajdoviPromenjeni(1L);
        assertEquals(v + 1, iz.getVerzija());
        verify(izvodjenjeRepository).findByIdForUpdate(IZ);
        verify(publisher).publishEvent(new IzvodjenjePromenjeno(IZ));
    }

    @Test
    void promenaTipaTrenutnogSlajdaPreracunavaFazu() {
        UzivoPrezentacijaPromene promene = new UzivoPrezentacijaPromene(service);
        idiNa(0);
        k(TipKomande.SLEDECI);
        assertEquals(1, iz.getKorak());
        // INFO -> PITANJE
        Slajd kaoPitanje = pitanje(11L, 1, TipPitanja.JEDAN_TACAN, null);
        slajdovi.set(0, kaoPitanje);
        promene.slajdoviPromenjeni(1L);
        stanje(Prikaz.SLAJD, 11L, 0, Faza.CEKA);

        // otvoreno PITANJE -> INFO bez postepenog: runda se zatvara
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        Slajd info = new Slajd();
        info.setId(11L);
        info.setRb(1);
        info.setTip(TipSlajda.INFO);
        info.setNaslov("Sada info");
        slajdovi.set(0, info);
        promene.slajdoviPromenjeni(1L);
        stanje(Prikaz.SLAJD, 11L, 0, null);
        assertNull(iz.getTrenutnaRundaId());
        assertNotNull(r.getZatvoreno());
    }

    @Test
    void manjeStavkiSkracujeKorak() {
        UzivoPrezentacijaPromene promene = new UzivoPrezentacijaPromene(service);
        idiNa(0);
        k(TipKomande.SLEDECI);
        k(TipKomande.SLEDECI);
        assertEquals(2, iz.getKorak());
        s1.setSadrzaj("- samo jedna");
        promene.slajdoviPromenjeni(1L);
        assertEquals(1, iz.getKorak());
    }

    @Test
    void zakljucajAktivnaRastucePoId() {
        Izvodjenje drugo = new Izvodjenje();
        drugo.setId(8L);
        when(izvodjenjeRepository.findIdsByPrezentacijaIdAndStatus(1L, StatusIzvodjenja.AKTIVNO))
                .thenReturn(List.of(IZ, 8L));
        when(izvodjenjeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(drugo));
        new UzivoPrezentacijaPromene(service).zakljucaj(1L);
        InOrder red = inOrder(izvodjenjeRepository);
        red.verify(izvodjenjeRepository).findByIdForUpdate(IZ);
        red.verify(izvodjenjeRepository).findByIdForUpdate(8L);
        verifyNoInteractions(publisher);
        assertEquals(1, iz.getVerzija(), "samo zaključavanje, bez promene");
    }

    @Test
    void promeneBezAktivnihIzvodjenjaNistaNeRade() {
        when(izvodjenjeRepository.findIdsByPrezentacijaIdAndStatus(2L, StatusIzvodjenja.AKTIVNO))
                .thenReturn(List.of());
        UzivoPrezentacijaPromene promene = new UzivoPrezentacijaPromene(service);
        promene.slajdoviPromenjeni(2L);
        promene.slajdObrisan(2L, 50L, 0);
        verifyNoInteractions(publisher);
    }

    // ---------------------------------------------------------------- 11. svaka komanda zaključava i objavljuje

    @ParameterizedTest
    @EnumSource(value = TipKomande.class, names = {"TAJMER", "TAJMER_PLUS", "TAJMER_MINUS"}, mode = EnumSource.Mode.EXCLUDE)
    void svakaKomandaZakljucavaObjavljujeIVracaStanje(TipKomande tip) {
        doS2Zatvoreno();
        proveriKomandu(tip);
    }

    @ParameterizedTest
    @EnumSource(value = TipKomande.class, names = {"TAJMER", "TAJMER_PLUS", "TAJMER_MINUS"})
    void tajmerKomandeZakljucavajuIObjavljuju(TipKomande tip) {
        idiNa(1);
        k(TipKomande.SLEDECI);
        proveriKomandu(tip);
    }

    void proveriKomandu(TipKomande tip) {
        clearInvocations(izvodjenjeRepository, publisher, stanjeService);
        long v = iz.getVerzija();
        service.komanda(IZ, new KomandaCmd(tip, tip == TipKomande.IDI_NA ? 0 : null));
        verify(izvodjenjeRepository).findByIdForUpdate(IZ);
        verify(publisher).publishEvent(new IzvodjenjePromenjeno(IZ));
        InOrder red = inOrder(izvodjenjeRepository, stanjeService);
        red.verify(izvodjenjeRepository).flush();
        red.verify(stanjeService).nastavnicko(IZ);
        assertEquals(v + 1, iz.getVerzija());
    }

    // ---------------------------------------------------------------- moderacija

    Ucesnik ucesnik(long id, String ime) {
        Ucesnik u = new Ucesnik();
        u.setId(id);
        u.setIme(ime);
        u.setIzvodjenje(iz);
        when(ucesnikRepository.findByIdAndIzvodjenjeId(id, IZ)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void preimenuj() {
        Ucesnik u = ucesnik(31L, "Ana");
        when(imenaUcesnika.validiraj("  ana ")).thenReturn("ana");
        when(imenaUcesnika.jedinstvenoIme(IZ, "ana", 31L)).thenReturn("ana 2");
        long v = iz.getVerzija();
        service.preimenuj(IZ, 31L, "  ana ");
        assertEquals("ana 2", u.getIme());
        assertEquals(v + 1, iz.getVerzija());
        verify(izvodjenjeRepository).findByIdForUpdate(IZ);
        verify(publisher).publishEvent(new IzvodjenjePromenjeno(IZ));
    }

    @Test
    void preimenujNepostojecegIliIzbacenog404() {
        when(ucesnikRepository.findByIdAndIzvodjenjeId(32L, IZ)).thenReturn(Optional.empty());
        SystemException e = assertThrows(SystemException.class, () -> service.preimenuj(IZ, 32L, "Ana"));
        assertEquals(404, e.getCode());
        assertEquals("Učesnik nije pronađen.", e.getMessage());
        Ucesnik u = ucesnik(33L, "Bojan");
        u.setIzbacen(true);
        e = assertThrows(SystemException.class, () -> service.preimenuj(IZ, 33L, "Ana"));
        assertEquals(404, e.getCode());
    }

    @Test
    void izbaci() {
        Ucesnik u = ucesnik(31L, "Ana");
        long v = iz.getVerzija();
        service.izbaci(IZ, 31L);
        assertTrue(u.isIzbacen());
        assertEquals(v + 1, iz.getVerzija());
        verify(publisher).publishEvent(new UcesnikIzbacen(IZ, 31L));
        verify(publisher).publishEvent(new IzvodjenjePromenjeno(IZ));
    }

    @Test
    void moderacijaNaZavrsenom410() {
        ucesnik(31L, "Ana");
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        assertEquals(410, assertThrows(SystemException.class, () -> service.izbaci(IZ, 31L)).getCode());
        assertEquals(410, assertThrows(SystemException.class, () -> service.preimenuj(IZ, 31L, "X")).getCode());
    }

    Odgovor odgovor(PitanjeRunda r, String tekst) {
        Odgovor o = new Odgovor();
        o.setRunda(r);
        o.setTekst(tekst);
        return o;
    }

    @Test
    void sakrijPostavljaSakrivenGrupiOdgovora() {
        idiNa(1);
        k(TipKomande.SLEDECI);
        PitanjeRunda r = runda();
        Odgovor a = odgovor(r, "  Čelik ");
        Odgovor b = odgovor(r, "celik");
        Odgovor c = odgovor(r, "beton");
        when(odgovorRepository.findAllByRundaId(r.getId())).thenReturn(List.of(a, b, c));
        long v = iz.getVerzija();

        service.sakrij(IZ, r.getId(), new SakrijCmd("celik", true));
        assertTrue(a.isSakriven());
        assertTrue(b.isSakriven());
        assertFalse(c.isSakriven());
        assertEquals(v + 1, iz.getVerzija());
        verify(publisher, atLeastOnce()).publishEvent(new IzvodjenjePromenjeno(IZ));

        service.sakrij(IZ, r.getId(), new SakrijCmd("celik", false));
        assertFalse(a.isSakriven());
        assertFalse(b.isSakriven());
    }

    @Test
    void sakrijTudjeRunde404() {
        SystemException e = assertThrows(SystemException.class,
                () -> service.sakrij(IZ, 999L, new SakrijCmd("x", true)));
        assertEquals(404, e.getCode());
        assertEquals("Runda nije pronađena.", e.getMessage());
    }

    // ---------------------------------------------------------------- brisanje

    @Test
    void obrisiAktivno409() {
        SystemException e = assertThrows(SystemException.class, () -> service.obrisi(IZ));
        assertEquals(409, e.getCode());
        assertEquals("Izvođenje je u toku.", e.getMessage());
        verify(izvodjenjeRepository, never()).delete(any());
    }

    @Test
    void obrisiZavrseno() {
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        service.obrisi(IZ);
        verify(izvodjenjeRepository).delete(iz);
    }
}
