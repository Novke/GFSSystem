package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link StanjeService#nastavnicko}, {@code rezultati} i {@code lista} nad mock repozitorijumima: S2 (JEDAN_TACAN) je
 * trenutni, runda 1 je zatvorena, runda 2 (posle PONOVI) otvorena sa rokom; Cvele je izbačen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NastavnickoStanjeTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 7, 12, 0);
    static final Long IZ = 5L;

    @Mock IzvodjenjeRepository izvodjenjeRepository;
    @Mock SlajdRepository slajdRepository;
    @Mock PitanjeRundaRepository rundaRepository;
    @Mock OdgovorRepository odgovorRepository;
    @Mock UcesnikRepository ucesnikRepository;

    final MutableClock clock = new MutableClock(T0.plusSeconds(5).atZone(ZONA).toInstant(), ZONA);
    final JsonMapper jsonMapper = JsonMapper.builder().build();
    final PovezanostRegistar povezanost = new PovezanostRegistar();
    StanjeService service;

    Prezentacija prez;
    Slajd s1;
    Slajd s2;
    Slajd s3;
    Izvodjenje iz;
    PitanjeRunda r1;
    PitanjeRunda r2;
    Ucesnik ana;
    Ucesnik bojan;
    Ucesnik cvele;
    final List<PitanjeRunda> runde = new ArrayList<>();
    final List<Odgovor> odgovori = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Predmet predmet = new Predmet();
        predmet.setId(7L);
        prez = new Prezentacija();
        prez.setId(1L);
        prez.setNaziv("Uvod");
        prez.setPredmet(predmet);
        s1 = new Slajd();
        s1.setId(11L);
        s1.setRb(1);
        s1.setTip(TipSlajda.INFO);
        s1.setSadrzaj("- a\n- b");
        s1.setPostepeno(true);
        s2 = pitanje(12L, 2, TipPitanja.JEDAN_TACAN);
        s3 = pitanje(13L, 3, TipPitanja.ANKETA);

        iz = new Izvodjenje();
        iz.setId(IZ);
        iz.setPrezentacija(prez);
        iz.setKod("123456");
        iz.setStatus(StatusIzvodjenja.AKTIVNO);
        iz.setPocetak(T0);
        iz.setPrikaz(Prikaz.SLAJD);
        iz.setTrenutniSlajdId(12L);
        iz.setFaza(Faza.OTVORENO);
        iz.setEkran(Ekran.CRN);
        iz.setTelefonPrikaz(TelefonPrikaz.DUGMAD);
        iz.setTakmicenje(true);
        iz.setVerzija(17);

        r1 = runda(201L, 1, T0.minusMinutes(2));
        r1.setZatvoreno(T0.minusMinutes(1));
        r2 = runda(202L, 2, T0);
        r2.setRok(T0.plusSeconds(20));
        r2.setTrajanjeMs(20_000L);
        iz.setTrenutnaRundaId(202L);

        ana = ucesnik(31L, "Ana", false, 1);
        bojan = ucesnik(32L, "Bojan", false, 2);
        cvele = ucesnik(33L, "Cvele", true, 3);
        // runda 1: Ana tačno (1000), Bojan netačno; runda 2: Bojan tačno (800), izbačeni Cvele tačno
        odgovori.add(odgovor(r1, ana, "121", true, 1000, 1));
        odgovori.add(odgovor(r1, bojan, "122", false, 0, 2));
        odgovori.add(odgovor(r2, bojan, "121", true, 800, 3));
        odgovori.add(odgovor(r2, cvele, "121", true, 900, 4));

        when(izvodjenjeRepository.findSaVezama(IZ)).thenReturn(Optional.of(iz));
        when(slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(1L)).thenReturn(List.of(s1, s2, s3));
        when(rundaRepository.findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(IZ)).thenReturn(runde);
        when(ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(IZ)).thenReturn(List.of(ana, bojan, cvele));
        when(odgovorRepository.findAllByRundaIzvodjenjeId(IZ)).thenReturn(odgovori);

        service = new StanjeService(izvodjenjeRepository, slajdRepository, rundaRepository, odgovorRepository,
                ucesnikRepository, povezanost, jsonMapper, clock);
    }

    Slajd pitanje(Long id, int rb, TipPitanja tip) {
        Pitanje p = new Pitanje();
        p.setId(id * 10);
        p.setTip(tip);
        p.setTekst("Pitanje " + id);
        for (int i = 0; i < 2; i++) {
            PitanjeOpcija o = new PitanjeOpcija();
            o.setId(id * 10 + i + 1);
            o.setRb(i + 1);
            o.setTekst("Opcija " + (i + 1));
            o.setTacna(tip != TipPitanja.ANKETA && i == 0);
            p.getOpcije().add(o);
        }
        Slajd s = new Slajd();
        s.setId(id);
        s.setRb(rb);
        s.setTip(TipSlajda.PITANJE);
        s.setPitanje(p);
        return s;
    }

    PitanjeRunda runda(Long id, int redniBroj, LocalDateTime otvoreno) {
        PitanjeRunda r = new PitanjeRunda();
        r.setId(id);
        r.setIzvodjenje(iz);
        r.setSlajdId(12L);
        r.setRedniBroj(redniBroj);
        r.setOtvoreno(otvoreno);
        r.setSnimak(jsonMapper.writeValueAsString(PitanjeSnimak.od(s2)));
        runde.add(r);
        return r;
    }

    Ucesnik ucesnik(Long id, String ime, boolean izbacen, int minut) {
        Ucesnik u = new Ucesnik();
        u.setId(id);
        u.setIme(ime);
        u.setIzbacen(izbacen);
        u.setIzvodjenje(iz);
        u.setKreirano(T0.minusMinutes(10 - minut));
        return u;
    }

    Odgovor odgovor(PitanjeRunda r, Ucesnik u, String opcije, boolean tacno, int poeni, int sekund) {
        Odgovor o = new Odgovor();
        o.setId(400L + odgovori.size());
        o.setRunda(r);
        o.setUcesnik(u);
        o.setOpcije(opcije);
        o.setTacno(tacno);
        o.setPoeni(poeni);
        o.setVremeMs(sekund * 1000L);
        o.setKreirano(r.getOtvoreno().plusSeconds(sekund));
        return o;
    }

    @Test
    void stanjeNaOtvorenomPitanju() {
        povezanost.povezan(31L, "s-1");
        povezanost.povezan(33L, "s-2");

        NastavnickoStanje st = service.nastavnicko(IZ);

        assertEquals(17, st.verzija());
        assertEquals(clock.millis(), st.serverVremeMs());
        assertEquals(Prikaz.SLAJD, st.prikaz());
        assertEquals(1, st.indeks());
        assertEquals(3, st.brojSlajdova());
        assertEquals(12L, st.trenutniSlajd().id());
        assertEquals(13L, st.sledeciSlajd().id());
        assertEquals(0, st.brojStavki());
        assertEquals(Faza.OTVORENO, st.faza());
        assertEquals(Ekran.CRN, st.ekran());
        assertEquals(new RundaInfo(202L, 2, T0.plusSeconds(20).atZone(ZONA).toInstant().toEpochMilli(), null, true),
                st.runda());

        // izvođenje: neizbačeni učesnici i broj postavljenih pitanja (rundi)
        assertEquals(2, st.izvodjenje().brojUcesnika());
        assertEquals(2, st.izvodjenje().brojPitanja());
        assertEquals(new PrezentacijaKratko(1L, "Uvod", 7L), st.izvodjenje().prezentacija());

        // rezultat uživo: tačnost uvek vidljiva nastavniku, izbačeni se ne broji
        assertEquals(1, st.brojOdgovora());
        Rezultat rez = st.rezultat();
        assertEquals(1, rez.ukupno());
        assertEquals(List.of(new RezultatOpcija(121L, "Opcija 1", 1, true), new RezultatOpcija(122L, "Opcija 2", 0, false)),
                rez.opcije());

        // učesnici bez izbačenih; poeni samo iz poslednje runde (Anin poen iz runde 1 više ne važi)
        assertEquals(List.of(new UcesnikStanje(31L, "Ana", 0, true, false), new UcesnikStanje(32L, "Bojan", 800, false, true)),
                st.ucesnici());
        assertEquals(1, st.brojPovezanih());
        assertEquals(List.of(new RangStavka(1, 32L, "Bojan", 800), new RangStavka(2, 31L, "Ana", 0)), st.rangLista());
    }

    @Test
    void posleBrisanjaSlajdaRundeOstajuGrupisanePoSnimku() {
        r1.setSlajdId(null);
        r2.setSlajdId(null);
        iz.setPrikaz(Prikaz.KRAJ);
        iz.setTrenutniSlajdId(null);
        iz.setFaza(null);
        iz.setTrenutnaRundaId(null);

        NastavnickoStanje st = service.nastavnicko(IZ);

        assertEquals(3, st.indeks());
        assertNull(st.trenutniSlajd());
        assertNull(st.sledeciSlajd());
        assertNull(st.runda());
        assertNull(st.rezultat());
        assertEquals(0, st.brojOdgovora());
        assertEquals(List.of(new RangStavka(1, 32L, "Bojan", 800), new RangStavka(2, 31L, "Ana", 0)), st.rangLista());
        assertFalse(st.ucesnici().get(1).odgovorio());
    }

    @Test
    void zatvorenaRundaBezRokaUStanju() {
        iz.setFaza(Faza.ZATVORENO);
        r2.setZatvoreno(T0.plusSeconds(3));
        NastavnickoStanje st = service.nastavnicko(IZ);
        assertEquals(new RundaInfo(202L, 2, null, null, false), st.runda());
    }

    @Test
    void pauziranTajmer() {
        r2.setRok(null);
        r2.setPreostaloMs(12_000L);
        NastavnickoStanje st = service.nastavnicko(IZ);
        assertEquals(new RundaInfo(202L, 2, null, 12_000L, false), st.runda());
    }

    @Test
    void prijavaIInfoSlajd() {
        iz.setPrikaz(Prikaz.PRIJAVA);
        iz.setTrenutniSlajdId(null);
        iz.setFaza(null);
        iz.setTrenutnaRundaId(null);
        NastavnickoStanje st = service.nastavnicko(IZ);
        assertEquals(-1, st.indeks());
        assertNull(st.trenutniSlajd());
        assertEquals(11L, st.sledeciSlajd().id());

        iz.setPrikaz(Prikaz.SLAJD);
        iz.setTrenutniSlajdId(11L);
        iz.setKorak(1);
        st = service.nastavnicko(IZ);
        assertEquals(0, st.indeks());
        assertEquals(2, st.brojStavki());
        assertEquals(1, st.korak());
    }

    @Test
    void rangListaNajvise10() {
        List<Ucesnik> mnogo = new ArrayList<>();
        for (int i = 0; i < 15; i++) mnogo.add(ucesnik(100L + i, "U" + i, false, i));
        when(ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(IZ)).thenReturn(mnogo);
        NastavnickoStanje st = service.nastavnicko(IZ);
        assertEquals(10, st.rangLista().size());
        assertEquals(15, st.ucesnici().size());
    }

    @Test
    void nepostojeceIzvodjenje404() {
        when(izvodjenjeRepository.findSaVezama(9L)).thenReturn(Optional.empty());
        SystemException e = assertThrows(SystemException.class, () -> service.nastavnicko(9L));
        assertEquals(404, e.getCode());
    }

    @Test
    void rezultatiPoRunduSaProcentomTacnih() {
        IzvodjenjeRezultati rez = service.rezultati(IZ);
        assertEquals(2, rez.pitanja().size());
        RezultatPitanja p1 = rez.pitanja().get(0);
        assertEquals(201L, p1.rundaId());
        assertEquals(12L, p1.slajdId());
        assertEquals(2, p1.rbSlajda());
        assertEquals(1, p1.redniBroj());
        assertEquals(2, p1.brojOdgovora());
        assertEquals(50, p1.procenatTacnih());
        assertEquals(PitanjeSnimak.od(s2), p1.pitanje());
        assertEquals(Boolean.TRUE, p1.rezultat().opcije().get(0).tacna());
        RezultatPitanja p2 = rez.pitanja().get(1);
        assertEquals(1, p2.brojOdgovora(), "izbačeni se ne broji");
        assertEquals(100, p2.procenatTacnih());
        assertEquals(2, rez.rangLista().size());
        assertEquals(2, rez.izvodjenje().brojPitanja());

        // obrisan slajd: rb nepoznat; anketa nema procenat
        r1.setSlajdId(null);
        r1.setSnimak(jsonMapper.writeValueAsString(PitanjeSnimak.od(s3)));
        rez = service.rezultati(IZ);
        assertNull(rez.pitanja().get(0).slajdId());
        assertNull(rez.pitanja().get(0).rbSlajda());
        assertNull(rez.pitanja().get(0).procenatTacnih());
    }

    @Test
    void listaSaBrojevimaJednimUpitom() {
        Izvodjenje drugo = new Izvodjenje();
        drugo.setId(6L);
        drugo.setPrezentacija(prez);
        drugo.setKod("654321");
        drugo.setStatus(StatusIzvodjenja.ZAVRSENO);
        when(izvodjenjeRepository.findZaListu(1L, null)).thenReturn(List.of(drugo, iz));
        when(ucesnikRepository.brojPoIzvodjenju(any())).thenReturn(List.<Object[]>of(new Object[]{5L, 2L}));
        when(rundaRepository.brojPoIzvodjenju(any())).thenReturn(List.<Object[]>of(new Object[]{5L, 3L}, new Object[]{6L, 1L}));

        List<IzvodjenjeInfo> lista = service.lista(1L, null);

        assertEquals(List.of(6L, 5L), lista.stream().map(IzvodjenjeInfo::id).toList());
        assertEquals(0, lista.get(0).brojUcesnika());
        assertEquals(1, lista.get(0).brojPitanja());
        assertEquals(2, lista.get(1).brojUcesnika());
        assertEquals(3, lista.get(1).brojPitanja());
        assertEquals(List.of(), service.lista(2L, StatusIzvodjenja.AKTIVNO));
    }
}
