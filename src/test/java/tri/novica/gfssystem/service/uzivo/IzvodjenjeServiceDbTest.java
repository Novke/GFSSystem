package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredmetRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link IzvodjenjeService} i {@link StanjeService} nad pravom MySQL bazom (kao CI), bez {@code @Transactional}:
 * svaki poziv se zaista commit-uje. Pokriva ono što Mockito ne vidi: zaključavanje prezentacije pri pokretanju,
 * fetch join upite stanja, brisanje trenutnog slajda tokom izvođenja (FK {@code ON DELETE SET NULL} i
 * {@code @DynamicUpdate} runde), zatvaranje po roku kroz pravi raspored posle commit-a i "ne čuvaj" na kraju.
 */
@SpringBootTest
class IzvodjenjeServiceDbTest {

    @Autowired IzvodjenjeService service;
    @Autowired StanjeService stanjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired GrupaRepository grupaRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    Predmet predmet;
    Grupa grupa;
    final List<Long> prezentacije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String oznaka = UUID.randomUUID().toString().substring(0, 8);
        predmet = new Predmet();
        predmet.setNaziv("Uživo izvođenje DB " + oznaka);
        predmet = predmetRepository.save(predmet);
        grupa = new Grupa();
        grupa.setNaziv("IZ-" + oznaka);
        grupa.setGodinaUpisa(2026);
        grupa = grupaRepository.save(grupa);
    }

    @AfterEach
    void tearDown() {
        for (Long id : prezentacije) {
            jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", id);
            try {
                prezentacijaService.obrisi(id);
            } catch (SystemException e) {
                // već obrisana
            }
        }
        grupaRepository.deleteById(grupa.getId());
        predmetRepository.deleteById(predmet.getId());
    }

    /** INFO (postepeno, 2 stavke), JEDAN_TACAN sa {@code sekunde}, ANKETA bez vremena. */
    PrezentacijaDetails prezentacija(Integer sekunde) {
        PrezentacijaDetails p = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Statika uživo", null));
        prezentacije.add(p.id());
        prezentacijaService.dodajSlajd(p.id(), new SlajdCmd(TipSlajda.INFO, "Uvod", "- a\n- b", null, null, true, null), null);
        prezentacijaService.dodajSlajd(p.id(), pitanje(TipPitanja.JEDAN_TACAN, sekunde), null);
        prezentacijaService.dodajSlajd(p.id(), pitanje(TipPitanja.ANKETA, null), null);
        return prezentacijaService.detalji(p.id());
    }

    static SlajdCmd pitanje(TipPitanja tip, Integer sekunde) {
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, new PitanjeCmd(tip, "Koja sila?", null,
                sekunde, List.of(new OpcijaCmd("F", true), new OpcijaCmd("M", false)), null, null, null, null, null,
                null, null, null));
    }

    NastavnickoStanje k(Long id, TipKomande tip) {
        return service.komanda(id, new KomandaCmd(tip, null));
    }

    long broj(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    Long ucesnik(Long izvodjenjeId, String ime) {
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
        jdbc.update("insert into ucesnici (izvodjenje_id, ime, token_hash, izbacen, kreirano) values (?, ?, ?, 0, now(6))",
                izvodjenjeId, ime, hash);
        return jdbc.queryForObject("select id from ucesnici where token_hash = ?", Long.class, hash);
    }

    void odgovor(Long rundaId, Long ucesnikId, Long opcija, boolean tacno, int poeni) {
        jdbc.update("""
                insert into odgovori (runda_id, ucesnik_id, opcije, tacno, poeni, vreme_ms, sakriven, kreirano)
                values (?, ?, ?, ?, ?, 1000, 0, now(6))""", rundaId, ucesnikId, String.valueOf(opcija), tacno, poeni);
    }

    @Test
    void tokKomandiStanjeIBrisanjeTrenutnogSlajda() {
        PrezentacijaDetails p = prezentacija(20);
        SlajdDetails s2 = p.slajdovi().get(1);
        SlajdDetails s3 = p.slajdovi().get(2);
        Long tacna = s2.pitanje().opcije().get(0).id();

        IzvodjenjeInfo info = service.pokreni(p.id(), new PokreniCmd(true, grupa.getId(), null));
        Long id = info.id();
        assertTrue(info.kod().matches("\\d{6}"));
        assertEquals(new GrupaKratko(grupa.getId(), grupa.getNaziv()), info.grupa());
        assertTrue(info.cuvanje());
        assertEquals(info.kod(), jdbc.queryForObject("select aktivan_kod from izvodjenja where id = ?", String.class, id));
        // dok traje izvođenje, prezentacija se ne može obrisati
        SystemException e = assertThrows(SystemException.class, () -> prezentacijaService.obrisi(p.id()));
        assertEquals(409, e.getCode());

        NastavnickoStanje st = stanjeService.nastavnicko(id);
        assertEquals(Prikaz.PRIJAVA, st.prikaz());
        assertEquals(-1, st.indeks());
        assertEquals(1, st.verzija());
        assertEquals(p.slajdovi().get(0).id(), st.sledeciSlajd().id());

        k(id, TipKomande.SLEDECI);
        st = k(id, TipKomande.SLEDECI);
        assertEquals(1, st.korak());
        assertEquals(2, st.brojStavki());
        k(id, TipKomande.SLEDECI);
        st = k(id, TipKomande.SLEDECI);
        assertEquals(s2.id(), st.trenutniSlajd().id());
        assertEquals(Faza.CEKA, st.faza());
        assertNull(st.runda());

        LocalDateTime pre = LocalDateTime.now(clock);
        st = k(id, TipKomande.SLEDECI);
        assertEquals(Faza.OTVORENO, st.faza());
        assertEquals(6, st.verzija());
        Long rundaId = st.runda().id();
        assertTrue(st.runda().tajmerRadi());
        long rokMs = st.runda().rokMs();
        long ocekivano = pre.plusSeconds(20).atZone(clock.getZone()).toInstant().toEpochMilli();
        assertTrue(Math.abs(rokMs - ocekivano) < 2000, "rok ≈ sada + 20 s");
        assertTrue(jdbc.queryForObject("select snimak from pitanje_runde where id = ?", String.class, rundaId)
                .contains("Koja sila?"));

        Long ana = ucesnik(id, "Ana");
        Long bojan = ucesnik(id, "Bojan");
        odgovor(rundaId, ana, tacna, true, 900);
        st = stanjeService.nastavnicko(id);
        assertEquals(1, st.brojOdgovora());
        assertEquals(1, st.rezultat().opcije().get(0).broj());
        assertEquals(Boolean.TRUE, st.rezultat().opcije().get(0).tacna());
        assertEquals(List.of("Ana", "Bojan"), st.ucesnici().stream().map(UcesnikStanje::ime).toList());
        assertTrue(st.ucesnici().get(0).odgovorio());
        assertEquals(900, st.rangLista().get(0).poeni());

        // moderacija: preimenovanje u postojeće ime dobija sufiks; izbacivanje
        st = service.preimenuj(id, bojan, " ana ");
        assertEquals("ana 2", st.ucesnici().get(1).ime());
        st = service.izbaci(id, bojan);
        assertEquals(1, st.ucesnici().size());

        // brisanje trenutnog slajda dok je pitanje otvoreno: prelazi na slajd na istom mestu, istorija ostaje
        prezentacijaService.obrisiSlajd(s2.id());
        st = stanjeService.nastavnicko(id);
        assertEquals(s3.id(), st.trenutniSlajd().id());
        assertEquals(1, st.indeks());
        assertEquals(Faza.CEKA, st.faza());
        assertEquals(0, broj("select count(*) from pitanje_runde where id = ? and slajd_id is not null", rundaId));
        assertEquals(1, broj("select count(*) from pitanje_runde where id = ? and zatvoreno is not null", rundaId));
        assertEquals(900, st.rangLista().get(0).poeni(), "poeni iz runde obrisanog slajda ostaju");
        IzvodjenjeRezultati rez = service.rezultati(id);
        assertEquals(1, rez.pitanja().size());
        assertNull(rez.pitanja().get(0).slajdId());
        assertEquals(100, rez.pitanja().get(0).procenatTacnih());
        assertEquals("Koja sila?", rez.pitanja().get(0).pitanje().tekst());

        // izmena slajda tokom izvođenja: verzija raste
        long v = st.verzija();
        prezentacijaService.izmeniSlajd(s3.id(), pitanje(TipPitanja.ANKETA, 30));
        assertEquals(v + 1, stanjeService.nastavnicko(id).verzija());

        // sa čuvanjem: završetak ništa ne briše
        st = k(id, TipKomande.ZAVRSI);
        assertEquals(StatusIzvodjenja.ZAVRSENO, st.izvodjenje().status());
        assertNotNull(st.izvodjenje().kraj());
        assertNull(jdbc.queryForObject("select aktivan_kod from izvodjenja where id = ?", String.class, id));
        assertEquals(2, broj("select count(*) from ucesnici where izvodjenje_id = ?", id));
        assertEquals(1, broj("select count(*) from pitanje_runde where izvodjenje_id = ?", id));
        e = assertThrows(SystemException.class, () -> k(id, TipKomande.SLEDECI));
        assertEquals(410, e.getCode());
        assertEquals(List.of(id), service.lista(p.id(), StatusIzvodjenja.ZAVRSENO).stream().map(IzvodjenjeInfo::id).toList());
        assertEquals(1, service.lista(p.id(), null).get(0).brojUcesnika());
        assertEquals(1, service.lista(p.id(), null).get(0).brojPitanja());

        service.obrisi(id);
        assertEquals(0, broj("select count(*) from izvodjenja where id = ?", id));
        assertEquals(0, broj("select count(*) from ucesnici where izvodjenje_id = ?", id));
    }

    @Test
    void zavrsiBezCuvanjaBriseRundeOdgovoreIUcesnike() {
        PrezentacijaDetails p = prezentacija(null);
        Long id = service.pokreni(p.id(), new PokreniCmd(false, null, null)).id();
        service.komanda(id, new KomandaCmd(TipKomande.IDI_NA, 1));
        NastavnickoStanje st = k(id, TipKomande.SLEDECI);
        Long rundaId = st.runda().id();
        Long ana = ucesnik(id, "Ana");
        odgovor(rundaId, ana, p.slajdovi().get(1).pitanje().opcije().get(0).id(), true, 1000);
        st = k(id, TipKomande.PONOVI);
        assertNotEquals(rundaId, st.runda().id());
        assertEquals(0, st.rangLista().get(0).poeni(), "posle PONOVI stara runda odmah prestaje da se računa");

        st = k(id, TipKomande.ZAVRSI);
        assertEquals(StatusIzvodjenja.ZAVRSENO, st.izvodjenje().status());
        assertNull(st.runda());
        assertTrue(st.ucesnici().isEmpty());
        assertEquals(1, broj("select count(*) from izvodjenja where id = ?", id));
        assertEquals(0, broj("select count(*) from pitanje_runde where izvodjenje_id = ?", id));
        assertEquals(0, broj("select count(*) from ucesnici where izvodjenje_id = ?", id));
        assertEquals(0, broj("select count(*) from odgovori where runda_id = ?", rundaId));
    }

    @Test
    void pokreniBezPitanjaNikadNeCuva() {
        PrezentacijaDetails p = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Samo info", null));
        prezentacije.add(p.id());
        prezentacijaService.dodajSlajd(p.id(), new SlajdCmd(TipSlajda.INFO, "Uvod", null, null, null, false, null), null);
        IzvodjenjeInfo info = service.pokreni(p.id(), new PokreniCmd(true, grupa.getId(), null));
        assertFalse(info.cuvanje());
    }

    @Test
    void pitanjeSeZatvaraPoRokuKrozRaspored() throws InterruptedException {
        PrezentacijaDetails p = prezentacija(5);
        Long id = service.pokreni(p.id(), new PokreniCmd(false, null, null)).id();
        service.komanda(id, new KomandaCmd(TipKomande.IDI_NA, 1));
        NastavnickoStanje st = k(id, TipKomande.SLEDECI);
        assertEquals(Faza.OTVORENO, st.faza());
        long verzija = st.verzija();

        // rok 5 s + 1 s tolerancije; zakazano tek posle commit-a komande
        long kraj = System.currentTimeMillis() + 12_000;
        while (stanjeService.nastavnicko(id).faza() == Faza.OTVORENO && System.currentTimeMillis() < kraj) {
            Thread.sleep(200);
        }
        st = stanjeService.nastavnicko(id);
        assertEquals(Faza.ZATVORENO, st.faza());
        assertEquals(verzija + 1, st.verzija());
        assertNull(st.runda().rokMs());
    }
}
