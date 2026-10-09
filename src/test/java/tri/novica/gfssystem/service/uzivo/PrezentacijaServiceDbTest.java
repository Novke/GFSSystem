package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.TekstPrikaz;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PrezentacijaService} nad pravom MySQL bazom (Flyway V1..V7, kao CI): ono što Mockito ne vidi. Brisanje kroz
 * JPA ne sme ostaviti siročad u {@code pitanja}/{@code pitanje_opcije} (FK slajdovi -> pitanja nema kaskadu), zamena
 * opcija i INFO <-> PITANJE rade kroz {@code orphanRemoval}, fetch join slajdova ne duplira redove, a upit predavanja
 * za pokretanje filtrira i sortira kako treba. Bez {@code @Transactional}: svaki poziv servisa se zaista commit-uje.
 */
@SpringBootTest
class PrezentacijaServiceDbTest {

    @Autowired PrezentacijaService service;
    @Autowired PredmetRepository predmetRepository;
    @Autowired GrupaRepository grupaRepository;
    @Autowired PredavanjeRepository predavanjeRepository;
    @Autowired MedijRepository medijRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    Predmet predmet;
    Predmet drugiPredmet;
    Grupa grupa;
    String slikaId;
    final List<Long> prezentacije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String oznaka = UUID.randomUUID().toString().substring(0, 8);
        predmet = new Predmet();
        predmet.setNaziv("Uživo DB test " + oznaka);
        predmet = predmetRepository.save(predmet);
        drugiPredmet = new Predmet();
        drugiPredmet.setNaziv("Uživo DB test drugi " + oznaka);
        drugiPredmet = predmetRepository.save(drugiPredmet);
        grupa = new Grupa();
        grupa.setNaziv("UZ-" + oznaka);
        grupa.setGodinaUpisa(2026);
        grupa = grupaRepository.save(grupa);

        Medij m = new Medij();
        m.setId(UUID.randomUUID().toString());
        m.setNaziv("tabla.png");
        m.setMime("image/png");
        m.setVelicina(10);
        m.setKreirano(LocalDateTime.now(clock));
        slikaId = medijRepository.save(m).getId();
    }

    @AfterEach
    void tearDown() {
        for (Long id : prezentacije) {
            jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", id);
            try {
                service.obrisi(id);
            } catch (SystemException e) {
                // već obrisana u testu
            }
        }
        jdbc.update("delete from predavanja where predmet_id in (?, ?)", predmet.getId(), drugiPredmet.getId());
        grupaRepository.deleteById(grupa.getId());
        predmetRepository.deleteById(predmet.getId());
        predmetRepository.deleteById(drugiPredmet.getId());
        medijRepository.deleteById(slikaId);
    }

    PrezentacijaDetails nova(String naziv) {
        PrezentacijaDetails d = service.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), naziv, null));
        prezentacije.add(d.id());
        return d;
    }

    static SlajdCmd info(String naslov, String slikaId) {
        return new SlajdCmd(TipSlajda.INFO, naslov, null, slikaId, null, false, null);
    }

    SlajdCmd jedanTacan(String... opcije) {
        List<OpcijaCmd> lista = new ArrayList<>();
        for (int i = 0; i < opcije.length; i++) lista.add(new OpcijaCmd(opcije[i], i == 0));
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, "beleška", false, new PitanjeCmd(TipPitanja.JEDAN_TACAN,
                "Koja sila?", slikaId, 20, lista, null, null, null, null, null, null, null, null));
    }

    static SlajdCmd kratakTekst() {
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, new PitanjeCmd(TipPitanja.KRATAK_TEKST,
                "Materijal?", null, null, null, null, null, null, null, null, List.of("beton", "čelik"), null, null));
    }

    long broj(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    List<Long> pitanjaPrezentacije(Long prezentacijaId) {
        return jdbc.queryForList("select pitanje_id from slajdovi where prezentacija_id = ? and pitanje_id is not null",
                Long.class, prezentacijaId);
    }

    @Test
    void slajdoviPitanjaIOpcijeBezSirocadi() {
        Long id = nova("Statika 1").id();
        SlajdDetails a = service.dodajSlajd(id, info("A", slikaId), null);
        SlajdDetails q1 = service.dodajSlajd(id, jedanTacan("F", "M", "N"), null);
        SlajdDetails q2 = service.dodajSlajd(id, kratakTekst(), null);
        SlajdDetails b = service.dodajSlajd(id, info("B", null), a.id());
        assertNotNull(q1.pitanje().id());
        assertTrue(q1.pitanje().opcije().stream().allMatch(o -> o.id() != null));

        // fetch join (slika, pitanje, opcije) ne duplira slajdove; redosled i sadržaj
        PrezentacijaDetails d = service.detalji(id);
        assertEquals(List.of(a.id(), b.id(), q1.id(), q2.id()), d.slajdovi().stream().map(SlajdDetails::id).toList());
        assertEquals(List.of(1, 2, 3, 4), d.slajdovi().stream().map(SlajdDetails::rb).toList());
        assertEquals(2, d.brojPitanja());
        assertEquals(slikaId, d.slajdovi().get(0).slika().id());
        PitanjeDetails p1 = d.slajdovi().get(2).pitanje();
        assertEquals(List.of("F", "M", "N"), p1.opcije().stream().map(OpcijaDetails::tekst).toList());
        assertEquals(slikaId, p1.slika().id());
        PitanjeDetails p2 = d.slajdovi().get(3).pitanje();
        assertEquals(List.of("beton", "čelik"), p2.prihvatljiviOdgovori());
        assertEquals(TekstPrikaz.OBLAK, p2.tekstPrikaz());

        // zamena opcija: stare se brišu (orphanRemoval), pitanje ostaje isti red
        Long pitanje1 = q1.pitanje().id();
        SlajdCmd viseTacnih = new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, new PitanjeCmd(
                TipPitanja.VISE_TACNIH, "Koje sile?", null, null,
                List.of(new OpcijaCmd("x", true), new OpcijaCmd("y", true)), null, null, null, null, null, null, null, null));
        SlajdDetails q1izm = service.izmeniSlajd(q1.id(), viseTacnih);
        assertEquals(pitanje1, q1izm.pitanje().id());
        assertEquals(2, broj("select count(*) from pitanje_opcije where pitanje_id = ?", pitanje1));
        assertEquals(List.of("x", "y"), service.detalji(id).slajdovi().get(2).pitanje().opcije().stream()
                .map(OpcijaDetails::tekst).toList());

        // PITANJE -> INFO briše pitanje; INFO -> PITANJE pravi novo
        Long pitanje2 = q2.pitanje().id();
        service.izmeniSlajd(q2.id(), info("Sada info", null));
        assertEquals(0, broj("select count(*) from pitanja where id = ?", pitanje2));
        SlajdDetails bkaoPitanje = service.izmeniSlajd(b.id(), jedanTacan("da", "ne"));
        assertNotNull(bkaoPitanje.pitanje().id());

        // dupliranje slajda i redosled
        SlajdDetails kopija = service.duplirajSlajd(q1.id());
        assertNotEquals(pitanje1, kopija.pitanje().id());
        assertEquals(4, kopija.rb());
        List<Long> obrnuto = new ArrayList<>(service.detalji(id).slajdovi().stream().map(SlajdDetails::id).toList());
        java.util.Collections.reverse(obrnuto);
        assertEquals(obrnuto, service.redosled(id, obrnuto).slajdovi().stream().map(SlajdDetails::id).toList());
        assertEquals(obrnuto, service.detalji(id).slajdovi().stream().map(SlajdDetails::id).toList());

        // brisanje slajda sa pitanjem: nema siročadi, rb 1..n
        Long pitanjeB = bkaoPitanje.pitanje().id();
        service.obrisiSlajd(b.id());
        assertEquals(0, broj("select count(*) from pitanja where id = ?", pitanjeB));
        assertEquals(0, broj("select count(*) from pitanje_opcije where pitanje_id = ?", pitanjeB));
        assertEquals(List.of(1, 2, 3, 4), service.detalji(id).slajdovi().stream().map(SlajdDetails::rb).toList());
    }

    @Test
    void brisanjePrezentacijeBriseSveIOdbijaSeDokTrajeIzvodjenje() {
        Long id = nova("Statika 2").id();
        service.dodajSlajd(id, info("A", slikaId), null);
        SlajdDetails q = service.dodajSlajd(id, jedanTacan("F", "M"), null);
        service.dodajSlajd(id, kratakTekst(), null);

        // kopija ima svoja pitanja i opcije, iste slike
        PrezentacijaDetails kopija = service.dupliraj(id);
        prezentacije.add(kopija.id());
        assertEquals("Statika 2 (kopija)", kopija.naziv());
        assertEquals(3, kopija.slajdovi().size());
        List<Long> pitanjaOriginala = pitanjaPrezentacije(id);
        List<Long> pitanjaKopije = pitanjaPrezentacije(kopija.id());
        assertEquals(2, pitanjaKopije.size());
        assertTrue(pitanjaKopije.stream().noneMatch(pitanjaOriginala::contains));
        assertEquals(slikaId, kopija.slajdovi().get(1).pitanje().slika().id());

        // aktivno izvođenje: 409 i ništa obrisano; vidi se u listi
        jdbc.update("""
                insert into izvodjenja (prezentacija_id, kod, aktivan_kod, cuvanje, status, pocetak, prikaz, korak,
                  rezultati_prikazani, tacan_prikazan, rang_lista_prikazana, ekran, qr_prikazan, telefon_prikaz,
                  detalji_dozvoljeni, takmicenje, verzija)
                values (?, '987654', '987654', 1, 'AKTIVNO', now(6), 'PRIJAVA', 0, 0, 0, 0, 'NORMALAN', 0, 'DUGMAD', 1, 0, 1)""", id);
        Long izvodjenjeId = broj("select max(id) from izvodjenja where prezentacija_id = ?", id);
        jdbc.update("insert into pitanje_runde (izvodjenje_id, slajd_id, redni_broj, snimak, otvoreno) values (?, ?, 1, '{}', now(6))",
                izvodjenjeId, q.id());
        SystemException ex = assertThrows(SystemException.class, () -> service.obrisi(id));
        assertEquals(409, ex.getCode());
        assertEquals(3, broj("select count(*) from slajdovi where prezentacija_id = ?", id));
        PrezentacijaInfo info = service.lista(predmet.getId()).stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
        assertEquals(izvodjenjeId, info.aktivnoIzvodjenjeId());
        assertEquals(1, info.brojIzvodjenja());
        assertEquals(3, info.brojSlajdova());
        assertEquals(2, info.brojPitanja());

        // završeno izvođenje (sa rundom koja pokazuje na slajd) nestaje zajedno sa prezentacijom
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where id = ?", izvodjenjeId);
        service.obrisi(id);
        assertEquals(0, broj("select count(*) from prezentacije where id = ?", id));
        assertEquals(0, broj("select count(*) from slajdovi where prezentacija_id = ?", id));
        assertEquals(0, broj("select count(*) from izvodjenja where id = ?", izvodjenjeId));
        assertEquals(0, broj("select count(*) from pitanje_runde where izvodjenje_id = ?", izvodjenjeId));
        for (Long pitanjeId : pitanjaOriginala) {
            assertEquals(0, broj("select count(*) from pitanja where id = ?", pitanjeId));
            assertEquals(0, broj("select count(*) from pitanje_opcije where pitanje_id = ?", pitanjeId));
        }
        // kopija netaknuta
        assertEquals(3, service.detalji(kopija.id()).slajdovi().size());
        for (Long pitanjeId : pitanjaKopije) {
            assertEquals(1, broj("select count(*) from pitanja where id = ?", pitanjeId));
        }
        assertEquals(2, broj("select count(*) from pitanje_opcije where pitanje_id = ?",
                kopija.slajdovi().get(1).pitanje().id()));
    }

    Predavanje predavanje(Predmet p, int rb, LocalDate datum, Boolean zavrseno) {
        Predavanje pr = new Predavanje();
        pr.setPredmet(p);
        pr.setGrupa(grupa);
        pr.setRb(rb);
        pr.setDatum(datum);
        pr.setTema("Tema " + rb);
        pr.setZavrseno(zavrseno);
        return predavanjeRepository.save(pr);
    }

    @Test
    void predavanjaZaPokretanjeNezavrsenaIliDanasNajnovijaPrvaNajvise30() {
        Long id = nova("Statika 3").id();
        LocalDate danas = LocalDate.now(clock);
        predavanje(predmet, 1, danas.minusDays(200), true);              // završeno, staro: ne
        Predavanje danasZavrseno = predavanje(predmet, 2, danas, true);  // završeno, ali danas: da
        Predavanje nezavrseno = predavanje(predmet, 3, danas.minusDays(30), false);
        Predavanje bezStatusa = predavanje(predmet, 4, danas.minusDays(30), null);
        predavanje(drugiPredmet, 5, danas, false);                       // drugi predmet: ne
        for (int i = 0; i < 30; i++) {
            predavanje(predmet, 100 + i, danas.minusDays(40 + i), false);
        }

        List<PredavanjeZaPokretanjeInfo> lista = service.predavanjaZaPokretanje(id);

        assertEquals(30, lista.size());
        assertEquals(List.of(danasZavrseno.getId(), bezStatusa.getId(), nezavrseno.getId()),
                lista.subList(0, 3).stream().map(PredavanjeZaPokretanjeInfo::id).toList());
        assertEquals(new GrupaKratko(grupa.getId(), grupa.getNaziv()), lista.get(0).grupa());
        assertTrue(lista.get(0).zavrseno());
        assertEquals(danas.minusDays(40 + 26), lista.get(29).datum());
    }
}
