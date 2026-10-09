package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import tri.novica.gfssystem.dto.uzivo.JavnoIzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.UcesnikInfo;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** {@link UcesnikService}: info po kodu, prijava (ime, sufiks, granica 300, token i heš), "ja" i token za WebSocket. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UcesnikServiceTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 8, 10, 0);
    static final String KOD = "123456";
    static final String NEPOZNAT = "Izvođenje sa ovim kodom ne postoji ili je završeno.";

    @Mock IzvodjenjeRepository izvodjenjeRepository;
    @Mock UcesnikRepository ucesnikRepository;
    @Mock ApplicationEventPublisher publisher;

    final MutableClock clock = new MutableClock(T0.atZone(ZONA).toInstant(), ZONA);
    final TokenGenerator tokenGenerator = new TokenGenerator();
    final List<Ucesnik> sacuvani = new ArrayList<>();
    UcesnikService service;
    Izvodjenje iz;

    @BeforeEach
    void setUp() {
        Prezentacija prez = new Prezentacija();
        prez.setId(1L);
        prez.setNaziv("Statika 1");
        iz = izvodjenje(5L, KOD, prez);

        when(izvodjenjeRepository.findByAktivanKod(KOD)).thenReturn(Optional.of(iz));
        when(izvodjenjeRepository.findIdByAktivanKodAndStatus(KOD, StatusIzvodjenja.AKTIVNO)).thenReturn(Optional.of(5L));
        when(izvodjenjeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(iz));
        when(ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(5L)).thenAnswer(i -> List.copyOf(sacuvani));
        when(ucesnikRepository.countByIzvodjenjeIdAndIzbacenFalse(5L))
                .thenAnswer(i -> sacuvani.stream().filter(u -> !u.isIzbacen()).count());
        when(ucesnikRepository.save(any(Ucesnik.class))).thenAnswer(i -> {
            Ucesnik u = i.getArgument(0);
            u.setId(100L + sacuvani.size());
            sacuvani.add(u);
            return u;
        });

        service = new UcesnikService(izvodjenjeRepository, ucesnikRepository, new ImenaUcesnika(ucesnikRepository),
                tokenGenerator, publisher, clock);
    }

    static Izvodjenje izvodjenje(Long id, String kod, Prezentacija prez) {
        Izvodjenje i = new Izvodjenje();
        i.setId(id);
        i.setKod(kod);
        i.setAktivanKod(kod);
        i.setPrezentacija(prez);
        i.setStatus(StatusIzvodjenja.AKTIVNO);
        i.setPrikaz(Prikaz.PRIJAVA);
        return i;
    }

    static void ocekuj(int kod, String poruka, Runnable r) {
        SystemException e = assertThrows(SystemException.class, r::run);
        assertEquals(kod, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    // ---------------------------------------------------------------- info

    @Test
    void infoVracaNazivPrezentacije() {
        assertEquals(new JavnoIzvodjenjeInfo("Statika 1"), service.info(KOD));
    }

    @Test
    void nepoznatIliZavrsenKodJe404() {
        when(izvodjenjeRepository.findByAktivanKod("999999")).thenReturn(Optional.empty());
        ocekuj(404, NEPOZNAT, () -> service.info("999999"));
        ocekuj(404, NEPOZNAT, () -> service.prijavi("999999", "Ana"));
        // kod koji nije 6 cifara se i ne traži u bazi
        ocekuj(404, NEPOZNAT, () -> service.info("12345x"));
        ocekuj(404, NEPOZNAT, () -> service.info(null));
        verify(izvodjenjeRepository, never()).findByAktivanKod("12345x");
        verify(izvodjenjeRepository, never()).findIdByAktivanKodAndStatus(eq("12345x"), any());
        verify(ucesnikRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- prijava

    @Test
    void prijavaCuvaHesTokenaIVracaToken() {
        UcesnikService.Prijavljen p = service.prijavi(KOD, "  Ana   Anić ");

        assertEquals(new UcesnikInfo(100L, "Ana Anić", 5L), p.info());
        assertEquals(32, p.token().length());
        assertTrue(TokenGenerator.FORMAT.matcher(p.token()).matches());
        Ucesnik u = sacuvani.get(0);
        assertEquals(TokenHash.od(p.token()), u.getTokenHash());
        assertEquals(64, u.getTokenHash().length());
        assertTrue(u.getTokenHash().matches("[0-9a-f]{64}"));
        assertNotEquals(p.token(), u.getTokenHash());
        assertFalse(u.isIzbacen());
        assertSame(iz, u.getIzvodjenje());
        assertEquals(T0, u.getKreirano());
        verify(publisher).publishEvent(new IzvodjenjePromenjeno(5L));
    }

    @Test
    void prijavaZakljucavaIzvodjenjePreImenaIUpisa() {
        service.prijavi(KOD, "Ana");
        InOrder red = inOrder(izvodjenjeRepository, ucesnikRepository, publisher);
        red.verify(izvodjenjeRepository).findByIdForUpdate(5L);
        red.verify(ucesnikRepository).findAllByIzvodjenjeIdOrderByKreiranoAsc(5L);
        red.verify(ucesnikRepository).save(any());
        red.verify(publisher).publishEvent(any(IzvodjenjePromenjeno.class));
        // pre zaključavanja samo id: učitan entitet ne bi video završetak do kog je došlo dok se čekalo
        verify(izvodjenjeRepository, never()).findByAktivanKod(any());
    }

    @Test
    void prijavaNeMenjaVerziju() {
        iz.setVerzija(7);
        service.prijavi(KOD, "Ana");
        assertEquals(7, iz.getVerzija());
    }

    @Test
    void izvodjenjeZavrsenoDokSeCekaloNaZakljucavanjeJe404() {
        Izvodjenje zakljucano = izvodjenje(5L, KOD, iz.getPrezentacija());
        zakljucano.setStatus(StatusIzvodjenja.ZAVRSENO);
        zakljucano.setAktivanKod(null);
        when(izvodjenjeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(zakljucano));
        ocekuj(404, NEPOZNAT, () -> service.prijavi(KOD, "Ana"));
        verify(ucesnikRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void neispravnoIme() {
        ocekuj(400, "Ime mora imati od 1 do 40 znakova.", () -> service.prijavi(KOD, "  "));
        ocekuj(400, "Ime mora imati od 1 do 40 znakova.", () -> service.prijavi(KOD, null));
        ocekuj(400, "Ime mora imati od 1 do 40 znakova.", () -> service.prijavi(KOD, "a".repeat(41)));
        verify(ucesnikRepository, never()).save(any());
        // 40 znakova je dozvoljeno
        assertEquals("b".repeat(40), service.prijavi(KOD, "b".repeat(40)).info().ime());
    }

    @Test
    void kontrolniZnaciSeUklanjaju() {
        assertEquals("Ana", service.prijavi(KOD, "Ana\u0007").info().ime());
    }

    @Test
    void istoImeDobijaSufiks() {
        assertEquals("Ana", service.prijavi(KOD, "Ana").info().ime());
        assertEquals("ana 2", service.prijavi(KOD, "ana").info().ime());
        assertEquals("ana 3", service.prijavi(KOD, "ana").info().ime());
    }

    @Test
    void dugoImeKaoDuplikatSeSkracuje() {
        String ime = "x".repeat(40);
        service.prijavi(KOD, ime);
        String drugo = service.prijavi(KOD, ime).info().ime();
        assertEquals("x".repeat(38) + " 2", drugo);
        assertEquals(40, drugo.length());
    }

    @Test
    void izbaceniNeZauzimajuImeNiMesto() {
        service.prijavi(KOD, "Ana");
        sacuvani.get(0).setIzbacen(true);
        assertEquals("Ana", service.prijavi(KOD, "Ana").info().ime());
    }

    @Test
    void punoIzvodjenjeJe409() {
        for (int i = 0; i < 300; i++) {
            Ucesnik u = new Ucesnik();
            u.setId((long) i);
            u.setIme("U" + i);
            u.setIzbacen(false);
            sacuvani.add(u);
        }
        ocekuj(409, "Izvođenje je popunjeno.", () -> service.prijavi(KOD, "Ana"));
        verify(ucesnikRepository, never()).save(any());

        // izbačeni se ne računaju
        sacuvani.get(0).setIzbacen(true);
        assertEquals("Ana", service.prijavi(KOD, "Ana").info().ime());
    }

    // ---------------------------------------------------------------- ja i token

    Ucesnik saTokenom(Izvodjenje izv, String token, boolean izbacen) {
        Ucesnik u = new Ucesnik();
        u.setId(50L);
        u.setIme("Bojan");
        u.setIzvodjenje(izv);
        u.setIzbacen(izbacen);
        u.setTokenHash(TokenHash.od(token));
        when(ucesnikRepository.findByTokenHash(TokenHash.od(token))).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void jaVazeciToken() {
        String token = tokenGenerator.novi();
        saTokenom(iz, token, false);
        assertEquals(Optional.of(new UcesnikInfo(50L, "Bojan", 5L)), service.ja(KOD, token));
    }

    @Test
    void jaIzbacenogJePrazno() {
        String token = tokenGenerator.novi();
        saTokenom(iz, token, true);
        assertEquals(Optional.empty(), service.ja(KOD, token));
    }

    @Test
    void jaZaDrugoIzvodjenjeJePrazno() {
        Izvodjenje drugo = izvodjenje(6L, "654321", iz.getPrezentacija());
        String token = tokenGenerator.novi();
        saTokenom(drugo, token, false);
        assertEquals(Optional.empty(), service.ja(KOD, token));
    }

    @Test
    void jaZaZavrsenoIzvodjenjeJePrazno() {
        String token = tokenGenerator.novi();
        saTokenom(iz, token, false);
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        iz.setAktivanKod(null);
        assertEquals(Optional.empty(), service.ja(KOD, token));
    }

    @Test
    void jaNepoznatIliNeispravanToken() {
        when(ucesnikRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), service.ja(KOD, tokenGenerator.novi()));
        assertEquals(Optional.empty(), service.ja(KOD, null));
        assertEquals(Optional.empty(), service.ja(KOD, ""));
        assertEquals(Optional.empty(), service.ja(KOD, "kratak"));
        assertEquals(Optional.empty(), service.ja(KOD, "x".repeat(5000)));
        // neispravan oblik se i ne hešira ni ne traži
        verify(ucesnikRepository, times(1)).findByTokenHash(anyString());
    }

    @Test
    void poTokenuSamoAktivanINeizbacen() {
        String token = tokenGenerator.novi();
        Ucesnik u = saTokenom(iz, token, false);
        assertEquals(Optional.of(u), service.poTokenu(token));

        u.setIzbacen(true);
        assertEquals(Optional.empty(), service.poTokenu(token));

        u.setIzbacen(false);
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        assertEquals(Optional.empty(), service.poTokenu(token));

        assertEquals(Optional.empty(), service.poTokenu(null));
        assertEquals(Optional.empty(), service.poTokenu("nije-token"));
    }

    @Test
    void tokenHashJeSha256Hex() {
        // SHA-256("abc"), poznata vrednost
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenHash.od("abc"));
    }
}
