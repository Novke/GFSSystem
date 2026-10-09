package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.repository.uzivo.PrezentacijaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdRepository;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrezentacijaServiceTest {

    static final LocalDateTime SADA = LocalDateTime.of(2026, 10, 7, 12, 0);
    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final Clock CLOCK = Clock.fixed(SADA.atZone(ZONA).toInstant(), ZONA);
    static final String SLIKA = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";

    @Mock PrezentacijaRepository prezentacijaRepository;
    @Mock SlajdRepository slajdRepository;
    @Mock IzvodjenjeRepository izvodjenjeRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock PredavanjeRepository predavanjeRepository;
    @Mock MedijService medijService;
    @Mock MedijRepository medijRepository;
    @Mock ObjectProvider<PrezentacijaPromene> promeneProvider;
    @Mock PrezentacijaPromene promene;

    PrezentacijaService service;
    Predmet predmet;
    Prezentacija prez;
    Medij medij;
    /** "Tabela" slajdova: repozitorijum je lažan, čita i piše ovu listu. */
    List<Slajd> baza = new ArrayList<>();
    AtomicLong sledeciId = new AtomicLong(100);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new PrezentacijaService(prezentacijaRepository, slajdRepository, izvodjenjeRepository,
                predmetRepository, predavanjeRepository, medijService, medijRepository, promeneProvider, CLOCK);

        predmet = new Predmet();
        predmet.setId(7L);
        predmet.setNaziv("Statika");
        when(predmetRepository.findById(7L)).thenReturn(Optional.of(predmet));

        prez = new Prezentacija();
        prez.setId(1L);
        prez.setPredmet(predmet);
        prez.setNaziv("Uvod u statiku");
        prez.setOpis("Prvi čas");
        prez.setTakmicenje(true);
        prez.setTelefonPrikaz(TelefonPrikaz.PITANJE);
        prez.setDetaljiDozvoljeni(false);
        prez.setKreirano(SADA.minusDays(10));
        prez.setIzmenjeno(SADA.minusDays(3));
        when(prezentacijaRepository.findById(1L)).thenReturn(Optional.of(prez));
        when(prezentacijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(prez));
        when(prezentacijaRepository.save(any(Prezentacija.class))).thenAnswer(inv -> {
            Prezentacija p = inv.getArgument(0);
            if (p.getId() == null) p.setId(2L);
            return p;
        });

        medij = new Medij();
        medij.setId(SLIKA);
        medij.setNaziv("tabla.png");
        medij.setMime("image/png");
        medij.setVelicina(1234);
        when(medijService.postoji(SLIKA)).thenReturn(true);
        when(medijRepository.getReferenceById(SLIKA)).thenReturn(medij);
        when(medijRepository.findById(SLIKA)).thenReturn(Optional.of(medij));

        when(slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(anyLong())).thenAnswer(inv -> slajdovi(inv.getArgument(0)));
        when(slajdRepository.findById(anyLong())).thenAnswer(inv -> nadjiUBazi(inv.getArgument(0)));
        when(slajdRepository.findPrezentacijaId(anyLong()))
                .thenAnswer(inv -> nadjiUBazi(inv.getArgument(0)).map(s -> s.getPrezentacija().getId()));
        when(slajdRepository.countByPrezentacijaId(anyLong())).thenAnswer(inv -> (long) slajdovi(inv.getArgument(0)).size());
        when(slajdRepository.countByPrezentacijaIdAndTip(anyLong(), any())).thenAnswer(inv ->
                slajdovi(inv.getArgument(0)).stream().filter(s -> s.getTip() == inv.getArgument(1)).count());
        when(slajdRepository.save(any(Slajd.class))).thenAnswer(inv -> {
            Slajd s = inv.getArgument(0);
            dodeliId(s);
            if (!baza.contains(s)) baza.add(s);
            return s;
        });
        doAnswer(inv -> baza.remove((Slajd) inv.getArgument(0))).when(slajdRepository).delete(any(Slajd.class));
        doAnswer(inv -> {
            ((Iterable<Slajd>) inv.getArgument(0)).forEach(baza::remove);
            return null;
        }).when(slajdRepository).deleteAll(anyIterable());

        doAnswer(inv -> {
            ((Consumer<PrezentacijaPromene>) inv.getArgument(0)).accept(promene);
            return null;
        }).when(promeneProvider).ifAvailable(any());
    }

    List<Slajd> slajdovi(Long prezId) {
        return new ArrayList<>(baza.stream().filter(s -> s.getPrezentacija().getId().equals(prezId))
                .sorted(Comparator.comparingInt(Slajd::getRb)).toList());
    }

    Optional<Slajd> nadjiUBazi(Long id) {
        return baza.stream().filter(s -> id.equals(s.getId())).findFirst();
    }

    void dodeliId(Slajd s) {
        if (s.getId() == null) s.setId(sledeciId.getAndIncrement());
        Pitanje p = s.getPitanje();
        if (p != null) {
            if (p.getId() == null) p.setId(sledeciId.getAndIncrement());
            p.getOpcije().forEach(o -> {
                if (o.getId() == null) o.setId(sledeciId.getAndIncrement());
            });
        }
    }

    Slajd infoUBazi(String naslov, int rb) {
        Slajd s = new Slajd();
        s.setPrezentacija(prez);
        s.setRb(rb);
        s.setTip(TipSlajda.INFO);
        s.setNaslov(naslov);
        dodeliId(s);
        baza.add(s);
        return s;
    }

    Slajd pitanjeUBazi(int rb, TipPitanja tip, String... opcije) {
        Slajd s = new Slajd();
        s.setPrezentacija(prez);
        s.setRb(rb);
        s.setTip(TipSlajda.PITANJE);
        s.setBeleske("beleška");
        Pitanje p = new Pitanje();
        p.setTip(tip);
        p.setTekst("Koja sila?");
        p.setSlika(medij);
        p.setVremeSekunde(20);
        for (int i = 0; i < opcije.length; i++) {
            PitanjeOpcija o = new PitanjeOpcija();
            o.setPitanje(p);
            o.setRb(i + 1);
            o.setTekst(opcije[i]);
            o.setTacna(i == 0);
            p.getOpcije().add(o);
        }
        s.setPitanje(p);
        dodeliId(s);
        baza.add(s);
        return s;
    }

    static SlajdCmd infoCmd(String naslov) {
        return new SlajdCmd(TipSlajda.INFO, naslov, null, null, null, false, null);
    }

    static PitanjeCmd pitanjeCmd(TipPitanja tip, List<OpcijaCmd> opcije) {
        return new PitanjeCmd(tip, "Koliko nosača?", null, null, opcije, null, null, null, null, null, null, null, null);
    }

    static SlajdCmd pitanjeSlajdCmd(PitanjeCmd p) {
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, p);
    }

    static void greska(int kod, String poruka, org.junit.jupiter.api.function.Executable e) {
        SystemException ex = assertThrows(SystemException.class, e);
        assertEquals(kod, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    List<Integer> rbovi() {
        return slajdovi(1L).stream().map(Slajd::getRb).toList();
    }

    // ---------------------------------------------------------------- prezentacija

    @Test
    void kreirajPostavljaPodrazumevanaPodesavanjaIVreme() {
        PrezentacijaDetails d = service.kreiraj(new CreatePrezentacijaCmd(7L, "  Momenti sile ", "  "));

        verify(prezentacijaRepository).save(argThat(p ->
                !p.isTakmicenje() && p.getTelefonPrikaz() == TelefonPrikaz.DUGMAD && p.isDetaljiDozvoljeni()
                        && SADA.equals(p.getKreirano()) && SADA.equals(p.getIzmenjeno())
                        && p.getPredmet() == predmet && "Momenti sile".equals(p.getNaziv()) && p.getOpis() == null));
        assertEquals(2L, d.id());
        assertEquals("Momenti sile", d.naziv());
        assertEquals(new PredmetKratko(7L, "Statika"), d.predmet());
        assertEquals(TelefonPrikaz.DUGMAD, d.telefonPrikaz());
        assertFalse(d.takmicenje());
        assertTrue(d.detaljiDozvoljeni());
        assertEquals(SADA, d.izmenjeno());
        assertEquals(List.of(), d.slajdovi());
        assertEquals(0, d.brojSlajdova());
        assertNull(d.aktivnoIzvodjenjeId());
    }

    @Test
    void kreirajNepostojeciPredmetJe404() {
        greska(404, "Predmet nije pronađen.", () -> service.kreiraj(new CreatePrezentacijaCmd(99L, "X", null)));
        verify(prezentacijaRepository, never()).save(any());
    }

    @Test
    void kreirajBezPredmetaJe400() {
        greska(400, "Predmet je obavezan.", () -> service.kreiraj(new CreatePrezentacijaCmd(null, "X", null)));
    }

    @Test
    void kreirajPrazanIliPredugNazivJe400() {
        String poruka = "Naziv je obavezan (najviše 200 znakova).";
        greska(400, poruka, () -> service.kreiraj(new CreatePrezentacijaCmd(7L, "   ", null)));
        greska(400, poruka, () -> service.kreiraj(new CreatePrezentacijaCmd(7L, null, null)));
        greska(400, poruka, () -> service.kreiraj(new CreatePrezentacijaCmd(7L, "a".repeat(201), null)));
        greska(400, "Opis može imati najviše 1000 znakova.",
                () -> service.kreiraj(new CreatePrezentacijaCmd(7L, "X", "a".repeat(1001))));
        verify(prezentacijaRepository, never()).save(any());
        assertDoesNotThrow(() -> service.kreiraj(new CreatePrezentacijaCmd(7L, "a".repeat(200), "a".repeat(1000))));
    }

    @Test
    void nadjiNepostojecuJe404() {
        greska(404, "Prezentacija nije pronađena.", () -> service.nadji(99L));
        greska(404, "Prezentacija nije pronađena.", () -> service.detalji(99L));
    }

    @Test
    void listaBrojiSlajdovePitanjaIIzvodjenja() {
        infoUBazi("Uvod", 1);
        pitanjeUBazi(2, TipPitanja.JEDAN_TACAN, "a", "b");
        pitanjeUBazi(3, TipPitanja.SKALA);
        Izvodjenje aktivno = new Izvodjenje();
        aktivno.setId(55L);
        when(prezentacijaRepository.findAllByPredmetIdOrderByIzmenjenoDesc(7L)).thenReturn(List.of(prez));
        when(izvodjenjeRepository.countByPrezentacijaId(1L)).thenReturn(4L);
        when(izvodjenjeRepository.findFirstByPrezentacijaIdAndStatusOrderByPocetakDesc(1L, StatusIzvodjenja.AKTIVNO))
                .thenReturn(Optional.of(aktivno));

        List<PrezentacijaInfo> lista = service.lista(7L);

        assertEquals(1, lista.size());
        PrezentacijaInfo i = lista.get(0);
        assertEquals(3, i.brojSlajdova());
        assertEquals(2, i.brojPitanja());
        assertEquals(4, i.brojIzvodjenja());
        assertEquals(55L, i.aktivnoIzvodjenjeId());
        assertEquals(new PredmetKratko(7L, "Statika"), i.predmet());
        assertEquals(TelefonPrikaz.PITANJE, i.telefonPrikaz());
        assertTrue(i.takmicenje());
        assertFalse(i.detaljiDozvoljeni());
    }

    @Test
    void listaBezPredmetaVracaSve() {
        when(prezentacijaRepository.findAllByOrderByIzmenjenoDesc()).thenReturn(List.of(prez));
        assertEquals(1, service.lista(null).size());
        verify(prezentacijaRepository, never()).findAllByPredmetIdOrderByIzmenjenoDesc(any());
    }

    @Test
    void detaljiVracajuSlajdovePoRbSaPitanjemIOpcijama() {
        pitanjeUBazi(2, TipPitanja.JEDAN_TACAN, "F", "M");
        infoUBazi("Uvod", 1).setSlika(medij);

        PrezentacijaDetails d = service.detalji(1L);

        assertEquals(2, d.slajdovi().size());
        assertEquals(1, d.brojPitanja());
        SlajdDetails prvi = d.slajdovi().get(0);
        assertEquals("Uvod", prvi.naslov());
        assertEquals(new MedijInfo(SLIKA, "tabla.png", "image/png", 1234), prvi.slika());
        assertNull(prvi.pitanje());
        PitanjeDetails p = d.slajdovi().get(1).pitanje();
        assertEquals(TipPitanja.JEDAN_TACAN, p.tip());
        assertEquals("Koja sila?", p.tekst());
        assertEquals(SLIKA, p.slika().id());
        assertEquals(20, p.vremeSekunde());
        assertEquals(List.of("F", "M"), p.opcije().stream().map(OpcijaDetails::tekst).toList());
        assertEquals(List.of(1, 2), p.opcije().stream().map(OpcijaDetails::rb).toList());
        assertTrue(p.opcije().get(0).tacna());
        assertEquals("beleška", d.slajdovi().get(1).beleske());
    }

    @Test
    void izmeniMenjaNazivIPodesavanjaIIzmenjeno() {
        PrezentacijaDetails d = service.izmeni(1L,
                new UpdatePrezentacijaCmd(" Novi naziv ", "", false, TelefonPrikaz.DUGMAD, true));
        assertEquals("Novi naziv", prez.getNaziv());
        assertNull(prez.getOpis());
        assertFalse(prez.isTakmicenje());
        assertEquals(TelefonPrikaz.DUGMAD, prez.getTelefonPrikaz());
        assertTrue(prez.isDetaljiDozvoljeni());
        assertEquals(SADA, prez.getIzmenjeno());
        assertEquals("Novi naziv", d.naziv());
        greska(400, "Naziv je obavezan (najviše 200 znakova).",
                () -> service.izmeni(1L, new UpdatePrezentacijaCmd("", null, false, TelefonPrikaz.DUGMAD, true)));
    }

    @Test
    void obrisiSaAktivnimIzvodjenjemJe409() {
        pitanjeUBazi(1, TipPitanja.ANKETA, "a", "b");
        when(izvodjenjeRepository.existsByPrezentacijaIdAndStatus(1L, StatusIzvodjenja.AKTIVNO)).thenReturn(true);
        greska(409, "Prezentacija ima izvođenje u toku. Završi ga pre brisanja.", () -> service.obrisi(1L));
        verify(prezentacijaRepository, never()).delete(any());
        assertEquals(1, baza.size());
    }

    @Test
    void obrisiBriseSlajdoveKrozJpaPrePrezentacije() {
        Slajd s1 = infoUBazi("Uvod", 1);
        Slajd s2 = pitanjeUBazi(2, TipPitanja.JEDAN_TACAN, "a", "b");

        service.obrisi(1L);

        InOrder redom = inOrder(prezentacijaRepository, slajdRepository);
        redom.verify(prezentacijaRepository).findByIdForUpdate(1L);
        redom.verify(slajdRepository).deleteAll(argThat((Iterable<Slajd> it) -> {
            List<Slajd> l = new ArrayList<>();
            it.forEach(l::add);
            return l.containsAll(List.of(s1, s2)) && l.size() == 2;
        }));
        redom.verify(slajdRepository).flush();
        redom.verify(prezentacijaRepository).delete(prez);
        assertTrue(baza.isEmpty());
    }

    @Test
    void duplirajKopiraSlajdovePitanjaIOpcijeSaIstimSlikama() {
        prez.setNaziv("a".repeat(200));
        Slajd info = infoUBazi("Uvod", 1);
        info.setSlika(medij);
        info.setSadrzaj("- a\n- b");
        info.setPostepeno(true);
        Slajd pitanje = pitanjeUBazi(2, TipPitanja.VISE_TACNIH, "a", "b", "c");

        PrezentacijaDetails d = service.dupliraj(1L);

        assertEquals(2L, d.id());
        assertEquals(200, d.naziv().length());
        assertTrue(d.naziv().endsWith(" (kopija)"));
        assertEquals(TelefonPrikaz.PITANJE, d.telefonPrikaz());
        assertTrue(d.takmicenje());
        assertFalse(d.detaljiDozvoljeni());
        assertEquals("Prvi čas", d.opis());
        assertEquals(SADA, d.izmenjeno());
        verify(prezentacijaRepository).save(argThat(p -> p.getId() == 2L && SADA.equals(p.getKreirano())
                && p.getPredmet() == predmet));

        List<Slajd> kopije = slajdovi(2L);
        assertEquals(2, kopije.size());
        Slajd k1 = kopije.get(0), k2 = kopije.get(1);
        assertNotSame(info, k1);
        assertNotEquals(info.getId(), k1.getId());
        assertEquals(1, k1.getRb());
        assertSame(medij, k1.getSlika());
        assertEquals("- a\n- b", k1.getSadrzaj());
        assertTrue(k1.isPostepeno());

        assertEquals(2, k2.getRb());
        assertNotSame(pitanje.getPitanje(), k2.getPitanje());
        assertNotEquals(pitanje.getPitanje().getId(), k2.getPitanje().getId());
        assertSame(medij, k2.getPitanje().getSlika());
        assertEquals(20, k2.getPitanje().getVremeSekunde());
        assertEquals(List.of("a", "b", "c"), k2.getPitanje().getOpcije().stream().map(PitanjeOpcija::getTekst).toList());
        for (int i = 0; i < 3; i++) {
            PitanjeOpcija stara = pitanje.getPitanje().getOpcije().get(i);
            PitanjeOpcija nova = k2.getPitanje().getOpcije().get(i);
            assertNotSame(stara, nova);
            assertSame(k2.getPitanje(), nova.getPitanje());
            assertEquals(stara.getRb(), nova.getRb());
            assertEquals(stara.isTacna(), nova.isTacna());
        }
        // original netaknut
        assertEquals(2, slajdovi(1L).size());
        assertEquals("a".repeat(200), prez.getNaziv());
        assertEquals(2, d.slajdovi().size());
    }

    @Test
    void duplirajKratkiNazivSamoDobijaSufiks() {
        assertEquals("Uvod u statiku (kopija)", service.dupliraj(1L).naziv());
    }

    // ---------------------------------------------------------------- slajdovi

    @Test
    void dodajSlajdBezPoslePrimaRbBrojPlusJedan() {
        infoUBazi("A", 1);
        infoUBazi("B", 2);

        SlajdDetails d = service.dodajSlajd(1L, infoCmd(" C "), null);

        assertEquals(3, d.rb());
        assertEquals("C", d.naslov());
        assertNotNull(d.id());
        assertEquals(List.of("A", "B", "C"), slajdovi(1L).stream().map(Slajd::getNaslov).toList());
        assertEquals(SADA, prez.getIzmenjeno());
        verify(prezentacijaRepository).findByIdForUpdate(1L);
        verify(promene).slajdoviPromenjeni(1L);
    }

    @Test
    void dodajSlajdPosleUbacujeIPomeraOstale() {
        Slajd a = infoUBazi("A", 1);
        infoUBazi("B", 2);
        infoUBazi("C", 3);

        SlajdDetails d = service.dodajSlajd(1L, infoCmd("novi"), a.getId());

        assertEquals(2, d.rb());
        assertEquals(List.of("A", "novi", "B", "C"), slajdovi(1L).stream().map(Slajd::getNaslov).toList());
        assertEquals(List.of(1, 2, 3, 4), rbovi());
    }

    @Test
    void dodajSlajdPosleTudjegSlajdaJe400() {
        infoUBazi("A", 1);
        greska(400, "Slajd posle kog se dodaje nije u ovoj prezentaciji.", () -> service.dodajSlajd(1L, infoCmd("x"), 999L));
    }

    @Test
    void dvestaPrviSlajdJe400() {
        for (int i = 1; i <= 200; i++) infoUBazi("S" + i, i);
        greska(400, "Prezentacija može imati najviše 200 slajdova.", () -> service.dodajSlajd(1L, infoCmd("x"), null));
        assertEquals(200, baza.size());
        verify(promene, never()).slajdoviPromenjeni(any());
    }

    @Test
    void dodajSlajdSaPitanjemPraviPitanjeIOpcije() {
        SlajdDetails d = service.dodajSlajd(1L, pitanjeSlajdCmd(pitanjeCmd(TipPitanja.JEDAN_TACAN,
                List.of(new OpcijaCmd(" 2 ", false), new OpcijaCmd("3", true)))), null);

        PitanjeDetails p = d.pitanje();
        assertEquals(TipPitanja.JEDAN_TACAN, p.tip());
        assertEquals(List.of("2", "3"), p.opcije().stream().map(OpcijaDetails::tekst).toList());
        assertEquals(List.of(1, 2), p.opcije().stream().map(OpcijaDetails::rb).toList());
        Slajd s = slajdovi(1L).get(0);
        s.getPitanje().getOpcije().forEach(o -> assertSame(s.getPitanje(), o.getPitanje()));
    }

    @Test
    void dodajSlajdNeispravanJe400IBezUpisa() {
        greska(400, "Info slajd mora imati naslov, tekst ili sliku.", () -> service.dodajSlajd(1L, infoCmd(" "), null));
        assertTrue(baza.isEmpty());
        verify(slajdRepository, never()).save(any());
    }

    @Test
    void nepostojecaSlikaJe400() {
        when(medijService.postoji("nema")).thenReturn(false);
        greska(400, "Slika nije pronađena.",
                () -> service.dodajSlajd(1L, new SlajdCmd(TipSlajda.INFO, null, null, "nema", null, false, null), null));
        PitanjeCmd p = new PitanjeCmd(TipPitanja.SKALA, "Ocena?", "nema", null, null, null, null, null, null, null, null,
                null, null);
        greska(400, "Slika nije pronađena.", () -> service.dodajSlajd(1L, pitanjeSlajdCmd(p), null));
        assertTrue(baza.isEmpty());
    }

    @Test
    void dodajSlajdSaSlikomPostavljaReferencu() {
        SlajdDetails d = service.dodajSlajd(1L, new SlajdCmd(TipSlajda.INFO, null, null, SLIKA, null, false, null), null);
        assertEquals(SLIKA, d.slika().id());
        assertSame(medij, slajdovi(1L).get(0).getSlika());
    }

    @Test
    void izmeniSlajdMenjaTipPitanjaIZamenjujeOpcije() {
        Slajd s = pitanjeUBazi(1, TipPitanja.JEDAN_TACAN, "a", "b", "c");
        Pitanje pitanje = s.getPitanje();
        List<PitanjeOpcija> lista = pitanje.getOpcije();
        List<PitanjeOpcija> stare = new ArrayList<>(lista);

        SlajdDetails d = service.izmeniSlajd(s.getId(), pitanjeSlajdCmd(pitanjeCmd(TipPitanja.VISE_TACNIH,
                List.of(new OpcijaCmd("x", true), new OpcijaCmd("y", true)))));

        assertSame(pitanje, s.getPitanje(), "isto pitanje (id) ostaje, menja mu se sadržaj");
        assertEquals(TipPitanja.VISE_TACNIH, pitanje.getTip());
        assertSame(lista, pitanje.getOpcije(), "kolekcija sa orphanRemoval se ne zamenjuje, samo prazni i puni");
        assertEquals(2, lista.size());
        stare.forEach(o -> assertFalse(lista.contains(o)));
        assertEquals(List.of("x", "y"), lista.stream().map(PitanjeOpcija::getTekst).toList());
        assertEquals(List.of(1, 2), lista.stream().map(PitanjeOpcija::getRb).toList());
        lista.forEach(o -> assertSame(pitanje, o.getPitanje()));
        assertEquals("Koliko nosača?", pitanje.getTekst());
        assertNull(pitanje.getSlika(), "puna zamena: slika koja nije poslata se uklanja");
        assertNull(pitanje.getVremeSekunde());
        assertNull(s.getBeleske());
        assertEquals(TipPitanja.VISE_TACNIH, d.pitanje().tip());
        assertEquals(SADA, prez.getIzmenjeno());
        verify(slajdRepository).flush();
        verify(promene).slajdoviPromenjeni(1L);
    }

    @Test
    void izmeniSlajdUBrojBriseOpcije() {
        Slajd s = pitanjeUBazi(1, TipPitanja.JEDAN_TACAN, "a", "b");
        PitanjeCmd broj = new PitanjeCmd(TipPitanja.BROJ, "g = ?", null, 30, List.of(new OpcijaCmd("a", true)), 9.81,
                null, null, "m/s²", null, null, null, null);

        service.izmeniSlajd(s.getId(), pitanjeSlajdCmd(broj));

        Pitanje p = s.getPitanje();
        assertTrue(p.getOpcije().isEmpty());
        assertEquals(9.81, p.getBrojTacno());
        assertEquals(0.0, p.getBrojOdstupanje());
        assertEquals(OdstupanjeTip.APSOLUTNO, p.getOdstupanjeTip());
        assertEquals("m/s²", p.getJedinica());
        assertEquals(30, p.getVremeSekunde());
    }

    @Test
    void izmeniSlajdInfoUPitanjePraviPitanje() {
        Slajd s = infoUBazi("Uvod", 1);

        SlajdDetails d = service.izmeniSlajd(s.getId(), pitanjeSlajdCmd(pitanjeCmd(TipPitanja.TACNO_NETACNO,
                List.of(new OpcijaCmd("", true), new OpcijaCmd("", false)))));

        assertEquals(TipSlajda.PITANJE, s.getTip());
        assertNotNull(s.getPitanje());
        assertEquals(List.of("Tačno", "Netačno"), s.getPitanje().getOpcije().stream().map(PitanjeOpcija::getTekst).toList());
        assertNull(s.getNaslov());
        assertEquals(TipSlajda.PITANJE, d.tip());
        assertNotNull(d.pitanje());
        verify(promene).slajdoviPromenjeni(1L);
    }

    @Test
    void izmeniSlajdPitanjeUInfoBrisePitanje() {
        Slajd s = pitanjeUBazi(1, TipPitanja.ANKETA, "a", "b");

        SlajdDetails d = service.izmeniSlajd(s.getId(),
                new SlajdCmd(TipSlajda.INFO, "Pauza", "Vraćamo se za 5 min", SLIKA, "b", true, null));

        assertEquals(TipSlajda.INFO, s.getTip());
        assertNull(s.getPitanje(), "orphanRemoval briše pitanje i njegove opcije");
        assertEquals("Pauza", s.getNaslov());
        assertEquals("Vraćamo se za 5 min", s.getSadrzaj());
        assertSame(medij, s.getSlika());
        assertTrue(s.isPostepeno());
        assertNull(d.pitanje());
        assertEquals(1, d.rb());
    }

    @Test
    void izmeniNepostojeciSlajdJe404() {
        greska(404, "Slajd nije pronađen.", () -> service.izmeniSlajd(999L, infoCmd("x")));
        greska(404, "Slajd nije pronađen.", () -> service.obrisiSlajd(999L));
        greska(404, "Slajd nije pronađen.", () -> service.duplirajSlajd(999L));
    }

    @Test
    void izmeniSlajdNeispravanJe400IBezPromene() {
        Slajd s = infoUBazi("Uvod", 1);
        greska(400, "Pitanje sa jednim tačnim odgovorom mora imati tačno jedan tačan odgovor.",
                () -> service.izmeniSlajd(s.getId(), pitanjeSlajdCmd(pitanjeCmd(TipPitanja.JEDAN_TACAN,
                        List.of(new OpcijaCmd("a", false), new OpcijaCmd("b", false))))));
        assertEquals(TipSlajda.INFO, s.getTip());
        assertEquals("Uvod", s.getNaslov());
        verify(promene, never()).slajdoviPromenjeni(any());
    }

    @Test
    void obrisiSlajdPrenumerisePozivaSlajdObrisan() {
        infoUBazi("A", 1);
        Slajd b = infoUBazi("B", 2);
        infoUBazi("C", 3);
        infoUBazi("D", 4);

        service.obrisiSlajd(b.getId());

        verify(slajdRepository).delete(b);
        assertEquals(List.of("A", "C", "D"), slajdovi(1L).stream().map(Slajd::getNaslov).toList());
        assertEquals(List.of(1, 2, 3), rbovi());
        assertEquals(SADA, prez.getIzmenjeno());
        InOrder redom = inOrder(slajdRepository, promene);
        redom.verify(slajdRepository).flush();
        redom.verify(promene).slajdObrisan(1L, b.getId(), 1);
        verify(prezentacijaRepository).findByIdForUpdate(1L);
    }

    @Test
    void obrisiSlajdPopravljaRupeURb() {
        infoUBazi("A", 2);
        Slajd b = infoUBazi("B", 5);
        infoUBazi("C", 9);

        service.obrisiSlajd(b.getId());

        assertEquals(List.of(1, 2), rbovi());
        verify(promene).slajdObrisan(1L, b.getId(), 1);
    }

    @Test
    void duplirajSlajdUbacujeKopijuOdmahPosleOriginala() {
        infoUBazi("A", 1);
        Slajd p = pitanjeUBazi(2, TipPitanja.JEDAN_TACAN, "x", "y");
        infoUBazi("C", 3);

        SlajdDetails d = service.duplirajSlajd(p.getId());

        assertEquals(3, d.rb());
        assertNotEquals(p.getId(), d.id());
        assertNotEquals(p.getPitanje().getId(), d.pitanje().id());
        assertEquals(List.of("x", "y"), d.pitanje().opcije().stream().map(OpcijaDetails::tekst).toList());
        List<Slajd> sada = slajdovi(1L);
        assertEquals(List.of(1, 2, 3, 4), rbovi());
        assertSame(p, sada.get(1));
        assertEquals(d.id(), sada.get(2).getId());
        assertEquals("C", sada.get(3).getNaslov());
        assertSame(medij, sada.get(2).getPitanje().getSlika());
        assertEquals("beleška", sada.get(2).getBeleske());
        verify(promene).slajdoviPromenjeni(1L);
    }

    @Test
    void duplirajSlajdPreko200Je400() {
        Slajd prvi = infoUBazi("S1", 1);
        for (int i = 2; i <= 200; i++) infoUBazi("S" + i, i);
        greska(400, "Prezentacija može imati najviše 200 slajdova.", () -> service.duplirajSlajd(prvi.getId()));
    }

    @Test
    void redosledPostavljaRbPoListi() {
        Slajd a = infoUBazi("A", 1);
        Slajd b = infoUBazi("B", 2);
        Slajd c = infoUBazi("C", 3);

        PrezentacijaDetails d = service.redosled(1L, List.of(c.getId(), a.getId(), b.getId()));

        assertEquals(List.of("C", "A", "B"), d.slajdovi().stream().map(SlajdDetails::naslov).toList());
        assertEquals(List.of(1, 2, 3), d.slajdovi().stream().map(SlajdDetails::rb).toList());
        assertEquals(2, a.getRb());
        assertEquals(SADA, prez.getIzmenjeno());
        verify(prezentacijaRepository).findByIdForUpdate(1L);
        verify(promene).slajdoviPromenjeni(1L);
    }

    @Test
    void redosledKojiNijePermutacijaJe400() {
        Slajd a = infoUBazi("A", 1);
        Slajd b = infoUBazi("B", 2);
        Slajd c = infoUBazi("C", 3);
        String poruka = "Redosled mora sadržati tačno sve slajdove prezentacije.";

        greska(400, poruka, () -> service.redosled(1L, List.of(a.getId(), b.getId())));                       // fali
        greska(400, poruka, () -> service.redosled(1L, List.of(a.getId(), b.getId(), c.getId(), 999L)));      // višak
        greska(400, poruka, () -> service.redosled(1L, List.of(a.getId(), a.getId(), b.getId())));            // duplikat
        greska(400, poruka, () -> service.redosled(1L, List.of(a.getId(), b.getId(), 999L)));                 // tuđi
        greska(400, poruka, () -> service.redosled(1L, Arrays.asList(a.getId(), b.getId(), null)));           // null
        greska(400, poruka, () -> service.redosled(1L, null));

        assertEquals(List.of("A", "B", "C"), slajdovi(1L).stream().map(Slajd::getNaslov).toList());
        assertEquals(List.of(1, 2, 3), rbovi());
        verify(promene, never()).slajdoviPromenjeni(any());
    }

    /** Ruling 8: aktivna izvođenja se zaključavaju posle prezentacije, a pre bilo kog upisa slajdova. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"izmeni", "dodajSlajd", "izmeniSlajd", "obrisiSlajd",
            "duplirajSlajd", "redosled"})
    void izvodjenjaSeZakljucavajuPrePisanjaSlajdova(String operacija) {
        Slajd a = infoUBazi("A", 1);
        Slajd b = infoUBazi("B", 2);
        List<String> pisanjaPreZakljucavanja = new ArrayList<>();
        doAnswer(inv -> {
            mockingDetails(slajdRepository).getInvocations().stream()
                    .map(i -> i.getMethod().getName())
                    .filter(m -> m.startsWith("save") || m.startsWith("delete") || m.equals("flush"))
                    .forEach(pisanjaPreZakljucavanja::add);
            return null;
        }).when(promene).zakljucaj(1L);

        switch (operacija) {
            case "izmeni" -> service.izmeni(1L, new UpdatePrezentacijaCmd("Novo", null, false, TelefonPrikaz.DUGMAD, true));
            case "dodajSlajd" -> service.dodajSlajd(1L, infoCmd("C"), a.getId());
            case "izmeniSlajd" -> service.izmeniSlajd(b.getId(), infoCmd("B2"));
            case "obrisiSlajd" -> service.obrisiSlajd(b.getId());
            case "duplirajSlajd" -> service.duplirajSlajd(a.getId());
            case "redosled" -> service.redosled(1L, List.of(b.getId(), a.getId()));
            default -> fail(operacija);
        }

        InOrder redom = inOrder(prezentacijaRepository, promene, slajdRepository);
        redom.verify(prezentacijaRepository).findByIdForUpdate(1L);
        redom.verify(promene).zakljucaj(1L);
        redom.verify(slajdRepository).flush();
        assertEquals(List.of(), pisanjaPreZakljucavanja);
    }

    @Test
    void obrisiPrezentacijuIstoZakljucavaIzvodjenja() {
        service.obrisi(1L);
        InOrder redom = inOrder(prezentacijaRepository, promene, slajdRepository);
        redom.verify(prezentacijaRepository).findByIdForUpdate(1L);
        redom.verify(promene).zakljucaj(1L);
        redom.verify(slajdRepository).deleteAll(anyIterable());
    }

    @Test
    void bezImplementacijePromenaSePreskace() {
        reset(promeneProvider);   // ifAvailable bez bean-a ne radi ništa
        infoUBazi("A", 1);
        assertDoesNotThrow(() -> service.dodajSlajd(1L, infoCmd("B"), null));
        verify(promene, never()).slajdoviPromenjeni(any());
    }

    // ---------------------------------------------------------------- pokretanje

    @Test
    void predavanjaZaPokretanjeTraziPredmetDanasINajvise30() {
        Grupa g = new Grupa();
        g.setId(5L);
        g.setNaziv("GD-2025");
        Predavanje p = new Predavanje();
        p.setId(40L);
        p.setPredmet(predmet);
        p.setGrupa(g);
        p.setRb(3);
        p.setDatum(LocalDate.of(2026, 10, 7));
        p.setTema("Reakcije oslonaca");
        p.setZavrseno(false);
        Predavanje bezGrupe = new Predavanje();
        bezGrupe.setId(41L);
        bezGrupe.setPredmet(predmet);
        bezGrupe.setRb(2);
        when(predavanjeRepository.findZaPokretanje(eq(7L), eq(LocalDate.of(2026, 10, 7)), eq(PageRequest.of(0, 30))))
                .thenReturn(List.of(p, bezGrupe));

        List<PredavanjeZaPokretanjeInfo> lista = service.predavanjaZaPokretanje(1L);

        assertEquals(List.of(
                new PredavanjeZaPokretanjeInfo(40L, 3, LocalDate.of(2026, 10, 7), "Reakcije oslonaca", false,
                        new GrupaKratko(5L, "GD-2025")),
                new PredavanjeZaPokretanjeInfo(41L, 2, null, null, false, null)), lista);
    }

    @Test
    void predavanjaZaNepostojecuPrezentacijuJe404() {
        greska(404, "Prezentacija nije pronađena.", () -> service.predavanjaZaPokretanje(99L));
    }
}
