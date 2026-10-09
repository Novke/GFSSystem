package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.OdgovorCmd;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.OdgovorRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link OdgovorService}: JEDAN_TACAN sa opcijama 11 (tačna) i 12, runda 7 je trenutna i otvorena od T0, rok T0+20 s,
 * trajanje 20 s, takmičenje uključeno; sat je u T0+10 s.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OdgovorServiceTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 8, 10, 0);
    static final Long IZ = 5L;
    static final Long RUNDA = 7L;
    static final Long ANA = 31L;

    @Mock IzvodjenjeRepository izvodjenjeRepository;
    @Mock PitanjeRundaRepository rundaRepository;
    @Mock OdgovorRepository odgovorRepository;
    @Mock UcesnikRepository ucesnikRepository;
    @Mock ApplicationEventPublisher publisher;

    final MutableClock clock = new MutableClock(T0.plusSeconds(10).atZone(ZONA).toInstant(), ZONA);
    final JsonMapper jsonMapper = JsonMapper.builder().build();
    OdgovorService service;
    Izvodjenje iz;
    PitanjeRunda runda;
    Ucesnik ana;

    @BeforeEach
    void setUp() {
        iz = new Izvodjenje();
        iz.setId(IZ);
        iz.setStatus(StatusIzvodjenja.AKTIVNO);
        iz.setPrikaz(Prikaz.SLAJD);
        iz.setTrenutniSlajdId(12L);
        iz.setFaza(Faza.OTVORENO);
        iz.setTrenutnaRundaId(RUNDA);
        iz.setTakmicenje(true);
        iz.setVerzija(3);

        runda = runda(snimak(TipPitanja.JEDAN_TACAN));
        runda.setRok(T0.plusSeconds(20));
        runda.setTrajanjeMs(20_000L);

        ana = new Ucesnik();
        ana.setId(ANA);
        ana.setIme("Ana");
        ana.setIzvodjenje(iz);

        when(izvodjenjeRepository.findByIdForUpdate(IZ)).thenReturn(Optional.of(iz));
        when(rundaRepository.findById(RUNDA)).thenAnswer(i -> Optional.of(runda));
        when(ucesnikRepository.findByIdAndIzvodjenjeId(ANA, IZ)).thenReturn(Optional.of(ana));
        when(odgovorRepository.existsByRundaIdAndUcesnikId(anyLong(), anyLong())).thenReturn(false);
        when(odgovorRepository.save(any(Odgovor.class))).thenAnswer(i -> i.getArgument(0));

        service = new OdgovorService(izvodjenjeRepository, rundaRepository, odgovorRepository, ucesnikRepository,
                publisher, jsonMapper, clock);
    }

    PitanjeRunda runda(PitanjeSnimak s) {
        PitanjeRunda r = new PitanjeRunda();
        r.setId(RUNDA);
        r.setIzvodjenje(iz);
        r.setSlajdId(12L);
        r.setRedniBroj(1);
        r.setOtvoreno(T0);
        r.setSnimak(jsonMapper.writeValueAsString(s));
        return r;
    }

    static PitanjeSnimak snimak(TipPitanja tip) {
        List<OpcijaSnimak> opcije = switch (tip) {
            case JEDAN_TACAN, TACNO_NETACNO -> List.of(new OpcijaSnimak(11L, 1, "Sila", true),
                    new OpcijaSnimak(12L, 2, "Masa", false));
            case VISE_TACNIH -> List.of(new OpcijaSnimak(11L, 1, "A", true), new OpcijaSnimak(12L, 2, "B", false),
                    new OpcijaSnimak(13L, 3, "C", true));
            case ANKETA -> List.of(new OpcijaSnimak(11L, 1, "Da", false), new OpcijaSnimak(12L, 2, "Ne", false));
            default -> List.of();
        };
        return new PitanjeSnimak(120L, 12L, tip, "Koja sila?", null, null, opcije,
                tip == TipPitanja.BROJ ? 3.5 : null, tip == TipPitanja.BROJ ? 0.0 : null,
                tip == TipPitanja.BROJ ? OdstupanjeTip.APSOLUTNO : null, null,
                tip == TipPitanja.KRATAK_TEKST ? TekstPrikaz.OBLAK : null,
                tip == TipPitanja.KRATAK_TEKST ? List.of("Njutn") : null, null, null);
    }

    static OdgovorCmd opcije(Long... ids) {
        return new OdgovorCmd(RUNDA, List.of(ids), null, null, null);
    }

    Odgovor sacuvan() {
        ArgumentCaptor<Odgovor> c = ArgumentCaptor.forClass(Odgovor.class);
        verify(odgovorRepository).save(c.capture());
        return c.getValue();
    }

    void odbijen(String poruka, OdgovorCmd cmd) {
        odbijen(poruka, ANA, cmd);
    }

    void odbijen(String poruka, Long ucesnikId, OdgovorCmd cmd) {
        OdgovorOdbijen e = assertThrows(OdgovorOdbijen.class, () -> service.odgovori(IZ, ucesnikId, cmd));
        assertEquals(poruka, e.getMessage());
        verify(odgovorRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    // ---------------------------------------------------------------- prijem i ocena

    @Test
    void tacanOdgovorUPolaVremena() {
        service.odgovori(IZ, ANA, opcije(11L));

        Odgovor o = sacuvan();
        assertSame(runda, o.getRunda());
        assertSame(ana, o.getUcesnik());
        assertEquals("11", o.getOpcije());
        assertEquals(Boolean.TRUE, o.getTacno());
        assertEquals(750, o.getPoeni());
        assertEquals(10_000, o.getVremeMs());
        assertFalse(o.isSakriven());
        assertEquals(T0.plusSeconds(10), o.getKreirano());
        assertNull(o.getBroj());
        assertNull(o.getTekst());
        assertNull(o.getSkala());
        verify(publisher).publishEvent(new OdgovorPrimljen(IZ, ANA));
        // odgovor ne menja verziju izvođenja (verzija raste po komandi)
        assertEquals(3, iz.getVerzija());
    }

    @Test
    void odgovorZakljucavaIzvodjenjePrePrijema() {
        service.odgovori(IZ, ANA, opcije(11L));
        InOrder red = inOrder(izvodjenjeRepository, rundaRepository, odgovorRepository, publisher);
        red.verify(izvodjenjeRepository).findByIdForUpdate(IZ);
        red.verify(rundaRepository).findById(RUNDA);
        red.verify(odgovorRepository).save(any());
        red.verify(publisher).publishEvent(any(OdgovorPrimljen.class));
        verify(izvodjenjeRepository, never()).findById(any());
    }

    @Test
    void netacanOdgovorBezPoena() {
        service.odgovori(IZ, ANA, opcije(12L));
        Odgovor o = sacuvan();
        assertEquals(Boolean.FALSE, o.getTacno());
        assertEquals(0, o.getPoeni());
        assertEquals(10_000, o.getVremeMs());
    }

    @Test
    void bezTakmicenjaNemaPoena() {
        iz.setTakmicenje(false);
        service.odgovori(IZ, ANA, opcije(11L));
        Odgovor o = sacuvan();
        assertEquals(Boolean.TRUE, o.getTacno());
        assertEquals(0, o.getPoeni());
    }

    @Test
    void bezTajmeraHiljaduPoenaIVremeOdOtvaranja() {
        runda.setRok(null);
        runda.setTrajanjeMs(null);
        service.odgovori(IZ, ANA, opcije(11L));
        Odgovor o = sacuvan();
        assertEquals(1000, o.getPoeni());
        assertEquals(10_000, o.getVremeMs());
    }

    // ---------------------------------------------------------------- jedan odgovor po rundi

    @Test
    void drugiOdgovorIstogUcesnika() {
        when(odgovorRepository.existsByRundaIdAndUcesnikId(RUNDA, ANA)).thenReturn(true);
        odbijen("Već si odgovorio.", opcije(12L));
    }

    @Test
    void jedinstvenostUBaziJePoslednjaBrana() {
        when(odgovorRepository.save(any(Odgovor.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key uk_odgovori_runda_ucesnik"));
        OdgovorOdbijen e = assertThrows(OdgovorOdbijen.class, () -> service.odgovori(IZ, ANA, opcije(11L)));
        assertEquals("Već si odgovorio.", e.getMessage());
        verify(publisher, never()).publishEvent(any());
    }

    // ---------------------------------------------------------------- faza, runda, rok

    @Test
    void rundaKojaNijeTrenutna() {
        odbijen("Pitanje je zatvoreno.", new OdgovorCmd(6L, List.of(11L), null, null, null));
    }

    @Test
    void bezRunde() {
        odbijen("Pitanje je zatvoreno.", new OdgovorCmd(null, List.of(11L), null, null, null));
    }

    @Test
    void prazanZahtev() {
        odbijen("Pitanje je zatvoreno.", null);
    }

    @Test
    void zatvorenoPitanje() {
        iz.setFaza(Faza.ZATVORENO);
        odbijen("Pitanje je zatvoreno.", opcije(11L));
    }

    @Test
    void pitanjeKojeCeka() {
        iz.setFaza(Faza.CEKA);
        iz.setTrenutnaRundaId(null);
        odbijen("Pitanje je zatvoreno.", opcije(11L));
    }

    @Test
    void rundaZatvorenaUBazi() {
        runda.setZatvoreno(T0.plusSeconds(9));
        odbijen("Pitanje je zatvoreno.", opcije(11L));
    }

    @Test
    void rokPlusTolerancija() {
        clock.pomeri(Duration.ofMillis(10_000 + 999));   // rok + 999 ms
        service.odgovori(IZ, ANA, opcije(11L));
        Odgovor o = sacuvan();
        assertEquals(20_000, o.getVremeMs());
        assertEquals(500, o.getPoeni());
    }

    @Test
    void tacnoNaRokPlusSekund() {
        clock.pomeri(Duration.ofMillis(11_000));          // rok + 1000 ms: još se prima
        service.odgovori(IZ, ANA, opcije(11L));
        assertEquals(500, sacuvan().getPoeni());
    }

    @Test
    void posleRokaPlusSekund() {
        clock.pomeri(Duration.ofMillis(10_000 + 1001));  // rok + 1001 ms
        odbijen("Vreme je isteklo.", opcije(11L));
    }

    @Test
    void pauziranaRundaPrimaOdgovorIPauzaSeNeRacuna() {
        // pauzirano posle 5 s (preostalo 15 s); odgovor stiže dugo posle, dok je pauza
        runda.setRok(null);
        runda.setPreostaloMs(15_000L);
        clock.pomeri(Duration.ofSeconds(60));
        service.odgovori(IZ, ANA, opcije(11L));
        Odgovor o = sacuvan();
        assertEquals(5_000, o.getVremeMs());
        assertEquals(875, o.getPoeni());
    }

    @Test
    void posleNastavkaPauzaSeNeRacuna() {
        // pauzirano 30 s posle 5 s, nastavljeno u T0+35 s: novi rok T0+50 s; odgovor u T0+40 s = 10 s rada tajmera
        runda.setRok(T0.plusSeconds(50));
        clock.pomeri(Duration.ofSeconds(30));
        service.odgovori(IZ, ANA, opcije(11L));
        Odgovor o = sacuvan();
        assertEquals(10_000, o.getVremeMs());
        assertEquals(750, o.getPoeni());
    }

    @Test
    void ponovoOtvorenaRundaDajeNajmanjePoena() {
        // O na zatvorenu rundu: rok i preostalo obrisani, trajanje ostaje -> kasni odgovor = najmanje poena
        runda.setRok(null);
        runda.setPreostaloMs(null);
        clock.pomeri(Duration.ofSeconds(120));
        service.odgovori(IZ, ANA, opcije(11L));
        Odgovor o = sacuvan();
        assertEquals(20_000, o.getVremeMs());
        assertEquals(500, o.getPoeni());
    }

    @Test
    void naknadnoPokrenutTajmerBezTrajanja() {
        // T bez ograničenja: rok postoji, trajanje ne -> 1000 poena, vreme od otvaranja
        runda.setTrajanjeMs(null);
        runda.setRok(T0.plusSeconds(30));
        service.odgovori(IZ, ANA, opcije(11L));
        Odgovor o = sacuvan();
        assertEquals(1000, o.getPoeni());
        assertEquals(10_000, o.getVremeMs());
    }

    // ---------------------------------------------------------------- učesnik i izvođenje

    @Test
    void izbacenUcesnik() {
        ana.setIzbacen(true);
        odbijen("Nisi prijavljen na ovo izvođenje.", opcije(11L));
    }

    @Test
    void ucesnikDrugogIzvodjenja() {
        when(ucesnikRepository.findByIdAndIzvodjenjeId(99L, IZ)).thenReturn(Optional.empty());
        odbijen("Nisi prijavljen na ovo izvođenje.", 99L, opcije(11L));
    }

    @Test
    void bezUcesnika() {
        odbijen("Nisi prijavljen na ovo izvođenje.", null, opcije(11L));
    }

    @Test
    void zavrsenoIzvodjenje() {
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        odbijen("Izvođenje je završeno.", opcije(11L));
    }

    @Test
    void nepostojeceIzvodjenje() {
        when(izvodjenjeRepository.findByIdForUpdate(IZ)).thenReturn(Optional.empty());
        odbijen("Izvođenje je završeno.", opcije(11L));
    }

    // ---------------------------------------------------------------- oblik odgovora po tipu

    @Test
    void neispravanOblikNosiPorukuOcenjivaca() {
        odbijen("Izaberi tačno jedan odgovor.", opcije(11L, 12L));
        odbijen("Izaberi tačno jedan odgovor.", new OdgovorCmd(RUNDA, null, "3", null, null));
        odbijen("Nepoznat odgovor.", opcije(99L));
    }

    @Test
    void nullUOpcijamaJeNepoznatOdgovor() {
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        ids.add(null);
        odbijen("Nepoznat odgovor.", new OdgovorCmd(RUNDA, ids, null, null, null));
    }

    @Test
    void viseTacnihSortiraniIdJeviBezDuplikata() {
        runda = runda(snimak(TipPitanja.VISE_TACNIH));
        service.odgovori(IZ, ANA, opcije(13L, 11L, 13L));
        Odgovor o = sacuvan();
        assertEquals("11,13", o.getOpcije());
        assertEquals(Boolean.TRUE, o.getTacno());
    }

    @Test
    void anketaBezTacnosti() {
        runda = runda(snimak(TipPitanja.ANKETA));
        service.odgovori(IZ, ANA, opcije(12L));
        Odgovor o = sacuvan();
        assertEquals("12", o.getOpcije());
        assertNull(o.getTacno());
        assertEquals(0, o.getPoeni());
    }

    @Test
    void brojSaZarezom() {
        runda = runda(snimak(TipPitanja.BROJ));
        service.odgovori(IZ, ANA, new OdgovorCmd(RUNDA, List.of(11L), "3,5", "ignoriše se", 4));
        Odgovor o = sacuvan();
        assertEquals(3.5, o.getBroj());
        assertEquals(Boolean.TRUE, o.getTacno());
        assertNull(o.getOpcije());
        assertNull(o.getTekst());
        assertNull(o.getSkala());
    }

    @Test
    void neispravanBroj() {
        runda = runda(snimak(TipPitanja.BROJ));
        odbijen("Unesi broj.", new OdgovorCmd(RUNDA, null, "tri", null, null));
        odbijen("Unesi broj.", new OdgovorCmd(RUNDA, null, null, null, null));
        odbijen("Unesi broj.", new OdgovorCmd(RUNDA, null, "9".repeat(400), null, null));
    }

    @Test
    void kratakTekstSeCuvaTrimovan() {
        runda = runda(snimak(TipPitanja.KRATAK_TEKST));
        service.odgovori(IZ, ANA, new OdgovorCmd(RUNDA, null, null, "  Њутн  ", null));
        Odgovor o = sacuvan();
        assertEquals("Њутн", o.getTekst());
        assertEquals(Boolean.TRUE, o.getTacno());
    }

    @Test
    void predugTekst() {
        runda = runda(snimak(TipPitanja.KRATAK_TEKST));
        odbijen("Odgovor mora imati od 1 do 200 znakova.", new OdgovorCmd(RUNDA, null, null, "a".repeat(201), null));
        odbijen("Odgovor mora imati od 1 do 200 znakova.", new OdgovorCmd(RUNDA, null, null, "   ", null));
    }

    @Test
    void skala() {
        runda = runda(snimak(TipPitanja.SKALA));
        service.odgovori(IZ, ANA, new OdgovorCmd(RUNDA, null, null, null, 4));
        Odgovor o = sacuvan();
        assertEquals(4, o.getSkala());
        assertNull(o.getTacno());
        assertEquals(0, o.getPoeni());
    }

    @Test
    void skalaVanOpsega() {
        runda = runda(snimak(TipPitanja.SKALA));
        odbijen("Izaberi vrednost od 1 do 5.", new OdgovorCmd(RUNDA, null, null, null, 6));
    }
}
