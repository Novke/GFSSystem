package tri.novica.gfssystem.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import tri.novica.gfssystem.dto.onboarding.*;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnboardingServiceTest {

    static final String TOKEN = "abcdefghjkmnpqrstuvwxyzABCDEFGH2";   // 32 znaka
    static final LocalDateTime SADA = LocalDateTime.of(2026, 10, 7, 12, 0);
    static final Clock CLOCK = Clock.fixed(SADA.atZone(ZoneId.of("Europe/Belgrade")).toInstant(), ZoneId.of("Europe/Belgrade"));

    @Mock OnboardingSesijaRepository sesijaRepository;
    @Mock PrijavaRepository prijavaRepository;
    @Mock StudentRepository studentRepository;
    @Mock GrupaRepository grupaRepository;
    @Mock TokenGenerator tokenGenerator;

    OnboardingService service;
    Grupa grupa;

    @BeforeEach
    void setUp() {
        service = new OnboardingService(sesijaRepository, prijavaRepository, studentRepository, grupaRepository,
                tokenGenerator, new ModelMapper(), CLOCK);
        grupa = new Grupa();
        grupa.setId(5L);
        grupa.setNaziv("TEST-2026");
        grupa.setGodinaUpisa(2026);
        when(prijavaRepository.save(any(Prijava.class))).thenAnswer(inv -> {
            Prijava p = inv.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            s.setId(900L);
            return s;
        });
    }

    OnboardingSesija sesija(boolean aktivna, LocalDateTime istice) {
        OnboardingSesija s = new OnboardingSesija();
        s.setId(1L);
        s.setGrupa(grupa);
        s.setToken(TOKEN);
        s.setAktivna(aktivna);
        s.setKreirano(SADA.minusDays(1));
        s.setIstice(istice);
        s.setMaxPrijava(200);
        when(sesijaRepository.findByTokenForUpdate(TOKEN)).thenReturn(Optional.of(s));
        when(sesijaRepository.findByToken(TOKEN)).thenReturn(Optional.of(s));
        when(sesijaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(s));
        when(sesijaRepository.findById(1L)).thenReturn(Optional.of(s));
        return s;
    }

    static PodnesiPrijavuCmd forma(String indeks, int godina) {
        return new PodnesiPrijavuCmd("Ana", "Anić", indeks, godina, "Ana@Example.com", "+381 64 123-4567",
                LocalDate.of(2007, 3, 1), "Subotica");
    }

    Prijava naCekanju(long id, String indeks, int godina, OnboardingSesija s) {
        Prijava p = new Prijava();
        p.setId(id);
        p.setSesija(s);
        p.setIme("Ana");
        p.setPrezime("Anić");
        p.setIndeks(indeks);
        p.setGodina(godina);
        p.setEmail("ana@example.com");
        p.setBrojTelefona("064123456");
        p.setDatumRodjenja(LocalDate.of(2007, 3, 1));
        p.setOpstina("Subotica");
        p.setStatus(StatusPrijave.NA_CEKANJU);
        p.setPodneto(SADA.minusHours(id));
        when(prijavaRepository.findByIdAndSesijaId(id, s.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void prijavaSeOdbijaKadStudentSaIstimIndeksomIGodinomPostoji() {
        sesija(true, SADA.plusDays(7));
        when(studentRepository.postojiStudent("GD12", 2026)).thenReturn(true);
        SystemException ex = assertThrows(SystemException.class, () -> service.podnesi(TOKEN, forma("gd 12", 2026), "1.2.3.4"));
        assertEquals(400, ex.getCode());
        assertEquals(OnboardingService.STUDENT_POSTOJI, ex.getMessage());
        verify(prijavaRepository, never()).save(any());
    }

    @Test
    void istiIndeksSaDrugomGodinomJeNovStudent() {
        sesija(true, SADA.plusDays(7));
        when(studentRepository.postojiStudent("GD12", 2024)).thenReturn(true);
        when(studentRepository.postojiStudent("GD12", 2026)).thenReturn(false);
        PodnetaPrijavaInfo info = service.podnesi(TOKEN, forma("GD12", 2026), "1.2.3.4");
        assertEquals(100L, info.getId());
        ArgumentCaptor<Prijava> c = ArgumentCaptor.forClass(Prijava.class);
        verify(prijavaRepository).save(c.capture());
        assertEquals("GD12", c.getValue().getIndeks());
        assertEquals("ana@example.com", c.getValue().getEmail());
        assertEquals(StatusPrijave.NA_CEKANJU, c.getValue().getStatus());
        assertEquals(SADA, c.getValue().getPodneto());
    }

    @Test
    void prijavaSeOdbijaKadIstiIndeksVecCekaUIstojSesiji() {
        sesija(true, SADA.plusDays(7));
        when(prijavaRepository.existsBySesijaIdAndIndeksAndGodinaAndStatus(1L, "GD12", 2026, StatusPrijave.NA_CEKANJU)).thenReturn(true);
        SystemException ex = assertThrows(SystemException.class, () -> service.podnesi(TOKEN, forma("GD12", 2026), "1.2.3.4"));
        assertEquals(400, ex.getCode());
        assertEquals(OnboardingService.PRIJAVA_CEKA, ex.getMessage());
    }

    @Test
    void zatvorenaSesijaVraca410() {
        sesija(false, SADA.plusDays(7));
        SystemException ex = assertThrows(SystemException.class, () -> service.podnesi(TOKEN, forma("GD12", 2026), "ip"));
        assertEquals(410, ex.getCode());
        assertFalse(service.javniInfo(TOKEN).isOtvorena());
    }

    @Test
    void isteklaSesijaVraca410() {
        sesija(true, SADA.minusMinutes(1));
        SystemException ex = assertThrows(SystemException.class, () -> service.podnesi(TOKEN, forma("GD12", 2026), "ip"));
        assertEquals(410, ex.getCode());
    }

    @Test
    void popunjenaSesijaVraca410() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        s.setMaxPrijava(2);
        when(prijavaRepository.countBySesijaId(1L)).thenReturn(2L);
        SystemException ex = assertThrows(SystemException.class, () -> service.podnesi(TOKEN, forma("GD12", 2026), "ip"));
        assertEquals(410, ex.getCode());
    }

    @Test
    void nepoznatIliLosTokenVraca404() {
        when(sesijaRepository.findByTokenForUpdate(anyString())).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(SystemException.class, () -> service.podnesi(TOKEN, forma("GD12", 2026), "ip")).getCode());
        assertEquals(404, assertThrows(SystemException.class, () -> service.javniInfo("../../x")).getCode());
        verify(sesijaRepository, never()).findByToken("../../x");
    }

    @Test
    void neispravnaPoljaDajuSvePorukeOdjednom() {
        sesija(true, SADA.plusDays(7));
        PodnesiPrijavuCmd cmd = new PodnesiPrijavuCmd(" ", "Anić", "G", 1999, "nije-email", "12", null, null);
        SystemException ex = assertThrows(SystemException.class, () -> service.podnesi(TOKEN, cmd, "ip"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("Ime je obavezno"));
        assertTrue(ex.getMessage().contains("Indeks mora imati"));
        assertTrue(ex.getMessage().contains("Godina upisa"));
        assertTrue(ex.getMessage().contains("Email nije ispravan"));
        assertTrue(ex.getMessage().contains("Broj telefona"));
    }

    @Test
    void podnosenjeZakljucavaRedSesije() {
        sesija(true, SADA.plusDays(7));
        service.podnesi(TOKEN, forma("GD12", 2026), "ip");
        verify(sesijaRepository).findByTokenForUpdate(TOKEN);
        verify(sesijaRepository, never()).findByToken(anyString());
    }

    @Test
    void prihvatanjePraviStudentaSaSvimPoljimaUGrupiSesije() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        Prijava p = naCekanju(7L, "GD12", 2026, s);
        service.prihvati(1L, 7L);
        ArgumentCaptor<Student> c = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(c.capture());
        Student st = c.getValue();
        assertEquals("Ana", st.getIme());
        assertEquals("Anić", st.getPrezime());
        assertEquals("GD12", st.getIndeks());
        assertEquals(2026, st.getGodina());
        assertEquals("ana@example.com", st.getEmail());
        assertEquals("064123456", st.getBrojTelefona());
        assertEquals(LocalDate.of(2007, 3, 1), st.getDatumRodjenja());
        assertEquals("Subotica", st.getOpstina());
        assertSame(grupa, st.getGrupa());
        assertEquals(StatusPrijave.PRIHVACENA, p.getStatus());
        assertEquals(900L, p.getStudent().getId());
        assertEquals(SADA, p.getObradjeno());
        verify(sesijaRepository).findByIdForUpdate(1L);
    }

    @Test
    void prihvatanjeKadStudentUMedjuvremenuPostojiVraca409() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        naCekanju(7L, "GD12", 2026, s);
        when(studentRepository.postojiStudent("GD12", 2026)).thenReturn(true);
        assertEquals(409, assertThrows(SystemException.class, () -> service.prihvati(1L, 7L)).getCode());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void vecObradjenaPrijavaSeNePrihvataPonovo() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        Prijava p = naCekanju(7L, "GD12", 2026, s);
        p.setStatus(StatusPrijave.PRIHVACENA);
        assertEquals(409, assertThrows(SystemException.class, () -> service.prihvati(1L, 7L)).getCode());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void prihvatiSvePreskaceKonfliktIVracaPoruku() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        Prijava a = naCekanju(1L, "GD5", 2026, s);
        Prijava b = naCekanju(2L, "GD12", 2026, s);
        when(prijavaRepository.findAllBySesijaIdAndStatusOrderByPodnetoAsc(1L, StatusPrijave.NA_CEKANJU)).thenReturn(List.of(a, b));
        when(studentRepository.postojiStudent("GD5", 2026)).thenReturn(true);
        OnboardingSesijaDetails d = service.prihvatiSve(1L);
        verify(studentRepository, times(1)).save(any(Student.class));
        assertEquals(StatusPrijave.NA_CEKANJU, a.getStatus());
        assertEquals("Preskočeno pri prihvatanju svih: student sa ovim indeksom i godinom upisa već postoji.", a.getNapomena());
        assertEquals(StatusPrijave.PRIHVACENA, b.getStatus());
        assertEquals("Prihvaćeno: 1. Preskočeno (indeks već postoji): GD5.", d.getPoruka());
    }

    @Test
    void odbijanjeUpisujeNapomenu() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        Prijava p = naCekanju(7L, "GD12", 2026, s);
        service.odbij(1L, 7L, new OdbijPrijavuCmd("  Pogrešna grupa "));
        assertEquals(StatusPrijave.ODBIJENA, p.getStatus());
        assertEquals("Pogrešna grupa", p.getNapomena());
        assertEquals(SADA, p.getObradjeno());
    }

    @Test
    void odbijanjeVecObradjenePrijaveVraca409() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        Prijava p = naCekanju(7L, "GD12", 2026, s);
        p.setStatus(StatusPrijave.PRIHVACENA);
        assertEquals(409, assertThrows(SystemException.class, () -> service.odbij(1L, 7L, null)).getCode());
        assertEquals(StatusPrijave.PRIHVACENA, p.getStatus());
        verify(prijavaRepository, never()).save(any());
    }

    @Test
    void odbijanjeSaPredugackomNapomenomVraca400() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        Prijava p = naCekanju(7L, "GD12", 2026, s);
        SystemException ex = assertThrows(SystemException.class,
                () -> service.odbij(1L, 7L, new OdbijPrijavuCmd("x".repeat(256))));
        assertEquals(400, ex.getCode());
        assertEquals("Napomena može imati najviše 255 znakova.", ex.getMessage());
        assertEquals(StatusPrijave.NA_CEKANJU, p.getStatus());
        verify(prijavaRepository, never()).save(any());
    }

    @Test
    void kreiranjeSesijePostavljaRokIToken() {
        when(grupaRepository.findById(5L)).thenReturn(Optional.of(grupa));
        when(tokenGenerator.novi()).thenReturn(TOKEN);
        when(sesijaRepository.save(any(OnboardingSesija.class))).thenAnswer(inv -> {
            OnboardingSesija s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        OnboardingSesijaInfo info = service.kreiraj(5L, null);
        assertEquals(TOKEN, info.getToken());
        assertEquals(SADA.plusDays(7), info.getIstice());
        assertEquals(200, info.getMaxPrijava());
        assertTrue(info.isOtvorena());
        assertEquals(400, assertThrows(SystemException.class,
                () -> service.kreiraj(5L, new CreateOnboardingCmd(61, null, null))).getCode());
    }

    @Test
    void ponovnoOtvaranjeIstekleSesijeProduzavaRok() {
        OnboardingSesija s = sesija(false, SADA.minusDays(2));
        when(sesijaRepository.save(any(OnboardingSesija.class))).thenAnswer(inv -> inv.getArgument(0));
        OnboardingSesijaInfo info = service.promeniAktivnost(1L, new UpdateOnboardingCmd(true, 3));
        assertTrue(s.isAktivna());
        assertEquals(SADA.plusDays(3), s.getIstice());
        assertTrue(info.isOtvorena());
    }

    @Test
    void izmenaPrijaveNormalizujeIProveravaDuplikat() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        Prijava p = naCekanju(7L, "GD12", 2026, s);
        UpdatePrijavaCmd cmd = new UpdatePrijavaCmd("Ana", "Anić", "gd 13", 2026, "ana@example.com", "064123456", null, null);
        PrijavaInfo info = service.izmeniPrijavu(1L, 7L, cmd);
        assertEquals("GD13", info.getIndeks());
        when(prijavaRepository.existsBySesijaIdAndIndeksAndGodinaAndStatusAndIdNot(1L, "GD14", 2026, StatusPrijave.NA_CEKANJU, 7L)).thenReturn(true);
        UpdatePrijavaCmd dupl = new UpdatePrijavaCmd("Ana", "Anić", "GD14", 2026, "ana@example.com", "064123456", null, null);
        assertEquals(400, assertThrows(SystemException.class, () -> service.izmeniPrijavu(1L, 7L, dupl)).getCode());
    }

    @Test
    void izmenaObradjenePrijaveVraca409() {
        OnboardingSesija s = sesija(true, SADA.plusDays(7));
        Prijava p = naCekanju(7L, "GD12", 2026, s);
        p.setStatus(StatusPrijave.ODBIJENA);
        UpdatePrijavaCmd cmd = new UpdatePrijavaCmd("Ana", "Anić", "GD13", 2026, "ana@example.com", "064123456", null, null);
        assertEquals(409, assertThrows(SystemException.class, () -> service.izmeniPrijavu(1L, 7L, cmd)).getCode());
        assertEquals("GD12", p.getIndeks());
        verify(prijavaRepository, never()).save(any());
    }

    @Test
    void cirilicniIndeksSeOdbijaALatinicniNormalizuje() {
        sesija(true, SADA.plusDays(7));
        // ćirilično "ГД12" (srpska tastatura telefona) bi inače zaobišlo dedupe protiv "GD12"
        SystemException ex = assertThrows(SystemException.class, () -> service.podnesi(TOKEN, forma("ГД12", 2026), "ip"));
        assertEquals(400, ex.getCode());
        assertEquals("Indeks mora imati od 2 do 20 znakova, latinicom (slova A-Z, cifre, / . -).", ex.getMessage());
        verify(prijavaRepository, never()).save(any());

        service.podnesi(TOKEN, forma("gd 12", 2026), "ip");
        ArgumentCaptor<Prijava> c = ArgumentCaptor.forClass(Prijava.class);
        verify(prijavaRepository).save(c.capture());
        assertEquals("GD12", c.getValue().getIndeks());
    }
}
