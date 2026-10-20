package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.banka.PonistavanjeInfo;
import tri.novica.gfssystem.dto.banka.StavkaPonistavanja;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.utility.Serija;
import tri.novica.gfssystem.validation.SlajdPP;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP metode {@link PrezentacijaService} nad pravom MySQL bazom (Flyway V1..V12, kao CI): serija na prezentaciji i slajdu,
 * Klodovo dodavanje slajda samo van izvođenja (ključ i provera izvođenja pre provere sadržaja), dupliranje bez serije i
 * poništavanje serije (neizvedena prezentacija samo sa slajdovima serije cela; ona sa Novicinim slajdom ostaje, bez slajdova
 * serije; slajdovi serije u tuđim prezentacijama uz prenumerisanje; izvedena i ona koja se izvodi preskočene). Bez
 * {@code @Transactional}: svaki poziv servisa se zaista commit-uje, pa test čisti za sobom.
 */
@SpringBootTest
class PrezentacijaServiceMcpDbTest {

    static final String IZVODI_SE = "Prezentacija se upravo izvodi; izmene posle časa.";
    static final String IZVODJENA = "Prezentacija je izvođena.";
    static final String UPRAVO_SE_IZVODI = "Prezentacija se upravo izvodi.";
    static final String NOVICINI_SLAJDOVI = "Prezentacija ima Novicine slajdove.";
    static final AtomicInteger KOD = new AtomicInteger((int) (Math.random() * 400_000) + 500_000);

    @Autowired PrezentacijaService service;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JdbcTemplate jdbc;

    Predmet predmet;
    /** Jedinstvena po testu: poništavanje serije gleda sve predmete. */
    String serija;
    String drugaSerija;
    final List<Long> prezentacije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String oznaka = UUID.randomUUID().toString().substring(0, 8);
        predmet = new Predmet();
        predmet.setNaziv("Prezentacije MCP DB test " + oznaka);
        predmet = predmetRepository.save(predmet);
        serija = "s-1-" + oznaka;
        drugaSerija = "s-2-" + oznaka;
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
        predmetRepository.deleteById(predmet.getId());
    }

    Long nova(String naziv, String serija) {
        Long id = service.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), naziv, null, null), serija).id();
        prezentacije.add(id);
        return id;
    }

    Long nova(String naziv) {
        Long id = service.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), naziv, null, null)).id();
        prezentacije.add(id);
        return id;
    }

    static SlajdCmd info(String naslov) {
        return new SlajdCmd(TipSlajda.INFO, naslov, null, null, null, false, null, null, null);
    }

    static SlajdCmd infoSaSadrzajem(String naslov, String sadrzaj) {
        return new SlajdCmd(TipSlajda.INFO, naslov, sadrzaj, null, null, false, null, null, null);
    }

    static SlajdCmd pitanje(String tekst) {
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, new PitanjeCmd(TipPitanja.JEDAN_TACAN,
                tekst, null, null, List.of(new OpcijaCmd("da", true), new OpcijaCmd("ne", false)),
                null, null, null, null, null, null, null, null), null, null);
    }

    /** Izvođenje kroz JDBC: aktivno (sa kodom) ili završeno. */
    Long izvodjenje(Long prezentacijaId, boolean aktivno) {
        String kod = String.valueOf(KOD.incrementAndGet());
        jdbc.update("""
                insert into izvodjenja (prezentacija_id, kod, aktivan_kod, cuvanje, status, pocetak, prikaz, korak,
                  rezultati_prikazani, tacan_prikazan, rang_lista_prikazana, ekran, qr_prikazan, telefon_prikaz,
                  detalji_dozvoljeni, takmicenje, verzija)
                values (?, ?, ?, 1, ?, now(6), 'PRIJAVA', 0, 0, 0, 0, 'NORMALAN', 0, 'DUGMAD', 1, 0, 1)""",
                prezentacijaId, kod, aktivno ? kod : null, aktivno ? "AKTIVNO" : "ZAVRSENO");
        return broj("select max(id) from izvodjenja where prezentacija_id = ?", prezentacijaId);
    }

    long broj(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    String serijaPrezentacije(Long id) {
        return jdbc.queryForObject("select serija from prezentacije where id = ?", String.class, id);
    }

    String serijaSlajda(Long id) {
        return jdbc.queryForObject("select serija from slajdovi where id = ?", String.class, id);
    }

    /** Id-jevi slajdova prezentacije po {@code rb}, uz proveru da je {@code rb} 1..n bez rupa. */
    List<Long> redosled(Long prezentacijaId) {
        List<Integer> rb = jdbc.queryForList("select rb from slajdovi where prezentacija_id = ? order by rb", Integer.class,
                prezentacijaId);
        for (int i = 0; i < rb.size(); i++) {
            assertEquals(i + 1, rb.get(i));
        }
        return jdbc.queryForList("select id from slajdovi where prezentacija_id = ? order by rb", Long.class, prezentacijaId);
    }

    static void greska(int kod, String poruka, Executable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv);
        assertEquals(kod, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    static String kratko(StavkaPonistavanja s) {
        return s.vrsta() + ":" + s.id() + (s.razlog() == null ? "" : ":" + s.razlog());
    }

    // ---------------------------------------------------------------- pravljenje i dodavanje

    @Test
    void kreirajUpisujeSeriju() {
        Long klodova = nova("Klodova", serija);
        Long rucna = nova("Ručna");
        assertEquals(serija, serijaPrezentacije(klodova));
        assertNull(serijaPrezentacije(rucna));

        long pre = broj("select count(*) from prezentacije where predmet_id = ?", predmet.getId());
        greska(400, Serija.PORUKA, () -> service.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Velika", null, null),
                "Velika-Slova"));
        assertEquals(pre, broj("select count(*) from prezentacije where predmet_id = ?", predmet.getId()));
    }

    @Test
    void dodajSlajdIzSerijeUpisujeSerijuIPostujePosle() {
        Long id = nova("Statika");
        Long a = service.dodajSlajd(id, info("A"), null).id();
        Long b = service.dodajSlajd(id, info("B"), null).id();

        SlajdDetails k = service.dodajSlajdIzSerije(id, info("K"), a, serija);
        assertEquals(2, k.rb());
        assertEquals("K", k.naslov());
        SlajdDetails q = service.dodajSlajdIzSerije(id, pitanje("Koja?"), null, serija);
        assertEquals(4, q.rb());
        assertEquals(TipSlajda.PITANJE, q.tip());

        assertEquals(List.of(a, k.id(), b, q.id()), redosled(id));
        assertEquals(serija, serijaSlajda(k.id()));
        assertEquals(serija, serijaSlajda(q.id()));
        assertNull(serijaSlajda(a));
        assertNull(serijaSlajda(b));

        // posle iz druge prezentacije, bez serije i serija van obrasca: 400, ništa upisano
        Long druga = nova("Druga");
        Long tudji = service.dodajSlajd(druga, info("X"), null).id();
        greska(400, PrezentacijaService.POSLE_NIJE_U_PREZENTACIJI, () -> service.dodajSlajdIzSerije(id, info("L"), tudji, serija));
        greska(400, Serija.PORUKA, () -> service.dodajSlajdIzSerije(id, info("L"), null, null));
        greska(400, Serija.PORUKA, () -> service.dodajSlajdIzSerije(id, info("L"), null, "s"));
        assertEquals(4, redosled(id).size());
    }

    @Test
    void dokSeIzvodiJe409AProveraSadrzajaJePosleKljuca() {
        Long id = nova("Uživo");
        service.dodajSlajd(id, info("A"), null);
        String spoljna = "Pogledaj ![a](https://x.invalid/a.png)";

        Long izvodjenjeId = izvodjenje(id, true);
        greska(409, IZVODI_SE, () -> service.dodajSlajdIzSerije(id, info("K"), null, serija));
        // provera sadržaja ide posle ključa i provere izvođenja: neispravan slajd dok traje čas je i dalje 409
        greska(409, IZVODI_SE, () -> service.dodajSlajdIzSerije(id, infoSaSadrzajem("K", spoljna), null, serija));
        assertEquals(1, redosled(id).size());

        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where id = ?", izvodjenjeId);
        greska(400, SlajdPP.SLIKA_VAN_GFS, () -> service.dodajSlajdIzSerije(id, infoSaSadrzajem("K", spoljna), null, serija));
        assertEquals(1, redosled(id).size());
        // posle časa (završeno izvođenje) dodavanje prolazi
        assertEquals(2, service.dodajSlajdIzSerije(id, info("K"), null, serija).rb());

        // nepostojeća prezentacija: 404 i za neispravan slajd (ključ pre provere)
        greska(404, PrezentacijaService.NIJE_PRONADJENA,
                () -> service.dodajSlajdIzSerije(Long.MAX_VALUE, infoSaSadrzajem("K", spoljna), null, serija));
    }

    @Test
    void najviseDvestaSlajdovaKaoDodajSlajd() {
        Long id = nova("Puna");
        List<Object[]> redovi = new ArrayList<>();
        for (int i = 1; i <= SlajdPP.MAX_SLAJDOVA; i++) {
            redovi.add(new Object[]{id, i, "S" + i});
        }
        jdbc.batchUpdate("insert into slajdovi (prezentacija_id, rb, tip, naslov, postepeno) values (?, ?, 'INFO', ?, 0)", redovi);
        greska(400, "Prezentacija može imati najviše 200 slajdova.",
                () -> service.dodajSlajdIzSerije(id, info("Višak"), null, serija));
        assertEquals(SlajdPP.MAX_SLAJDOVA, redosled(id).size());
    }

    @Test
    void dupliranjeNeKopiraSeriju() {
        Long id = nova("Klodova", serija);
        SlajdDetails k = service.dodajSlajdIzSerije(id, pitanje("Koja?"), null, serija);

        SlajdDetails kopijaSlajda = service.duplirajSlajd(k.id());
        assertEquals(2, kopijaSlajda.rb());
        assertNull(serijaSlajda(kopijaSlajda.id()));
        assertEquals(serija, serijaSlajda(k.id()));

        PrezentacijaDetails kopija = service.dupliraj(id);
        prezentacije.add(kopija.id());
        assertNull(serijaPrezentacije(kopija.id()));
        assertEquals(0, broj("select count(*) from slajdovi where prezentacija_id = ? and serija is not null", kopija.id()));
        assertEquals(2, kopija.slajdovi().size());
    }

    // ---------------------------------------------------------------- poništavanje serije

    /** Šest prezentacija sa slajdovima serije, kako ih vidi poništavanje. */
    record Scenario(Long neizvedena, Long mesovita, Long k4, Long nm, Long dk4, Long tudja, Long n1, Long k1, Long n2,
                    Long k2, Long izvedena, Long izvodiSe, Long k3, Long serijaKojaSeIzvodi) {
    }

    Scenario scenario() {
        // prezentacija serije bez izvođenja, samo sa slajdovima serije (i pitanjem): briše se cela
        Long neizvedena = nova("Neizvedena", serija);
        service.dodajSlajdIzSerije(neizvedena, info("K0"), null, serija);
        service.dodajSlajdIzSerije(neizvedena, pitanje("K0?"), null, serija);
        // prezentacija serije bez izvođenja u koju je Novica dodao slajd i duplirao Klodov: ostaje, briše se samo slajd serije
        Long mesovita = nova("Mešovita", serija);
        Long k4 = service.dodajSlajdIzSerije(mesovita, info("K4"), null, serija).id();
        Long nm = service.dodajSlajd(mesovita, info("Novicin"), null).id();
        Long dk4 = service.duplirajSlajd(k4).id();
        // Novicina prezentacija sa slajdom serije (pitanje), Novicinim slajdovima i slajdom druge serije
        Long tudja = nova("Tuđa");
        Long n1 = service.dodajSlajd(tudja, info("N1"), null).id();
        Long k1 = service.dodajSlajdIzSerije(tudja, pitanje("K1?"), null, serija).id();
        Long n2 = service.dodajSlajd(tudja, info("N2"), null).id();
        Long k2 = service.dodajSlajdIzSerije(tudja, info("K2"), null, drugaSerija).id();
        // prezentacija serije koja je izvođena (završeno izvođenje): preskače se cela
        Long izvedena = nova("Izvedena", serija);
        service.dodajSlajdIzSerije(izvedena, info("K"), null, serija);
        izvodjenje(izvedena, false);
        // Novicina prezentacija sa slajdom serije koja se upravo izvodi: slajd se preskače
        Long izvodiSe = nova("Izvodi se");
        service.dodajSlajd(izvodiSe, info("N"), null);
        Long k3 = service.dodajSlajdIzSerije(izvodiSe, info("K3"), null, serija).id();
        izvodjenje(izvodiSe, true);
        // prezentacija serije koja se upravo izvodi: preskače se cela, sa razlogom da se upravo izvodi
        Long serijaKojaSeIzvodi = nova("Serija uživo", serija);
        service.dodajSlajdIzSerije(serijaKojaSeIzvodi, info("K"), null, serija);
        izvodjenje(serijaKojaSeIzvodi, true);
        return new Scenario(neizvedena, mesovita, k4, nm, dk4, tudja, n1, k1, n2, k2, izvedena, izvodiSe, k3,
                serijaKojaSeIzvodi);
    }

    List<String> ocekivanoObrisano(Scenario s) {
        return List.of("PREZENTACIJA:" + s.neizvedena(), "SLAJD:" + s.k4(), "SLAJD:" + s.k1());
    }

    List<String> ocekivanoPreskoceno(Scenario s) {
        return List.of("PREZENTACIJA:" + s.mesovita() + ":" + NOVICINI_SLAJDOVI,
                "PREZENTACIJA:" + s.izvedena() + ":" + IZVODJENA,
                "SLAJD:" + s.k3() + ":" + UPRAVO_SE_IZVODI,
                "PREZENTACIJA:" + s.serijaKojaSeIzvodi() + ":" + UPRAVO_SE_IZVODI);
    }

    @Test
    void ponistiSerijuBezPotvrdeSamoNabraja() {
        Scenario s = scenario();
        long prezentacijaPre = broj("select count(*) from prezentacije where predmet_id = ?", predmet.getId());
        long slajdovaPre = broj("select count(*) from slajdovi sl join prezentacije p on p.id = sl.prezentacija_id"
                + " where p.predmet_id = ?", predmet.getId());

        PonistavanjeInfo info = service.ponistiSeriju(serija, false);

        assertFalse(info.potvrdjeno());
        assertEquals(ocekivanoObrisano(s), info.obrisano().stream().map(PrezentacijaServiceMcpDbTest::kratko).toList());
        assertEquals(ocekivanoPreskoceno(s), info.preskoceno().stream().map(PrezentacijaServiceMcpDbTest::kratko).toList());
        assertEquals("Neizvedena", info.obrisano().get(0).naziv());
        assertEquals("Mešovita, slajd 1: K4", info.obrisano().get(1).naziv());
        assertEquals("Tuđa, slajd 2: K1?", info.obrisano().get(2).naziv());
        assertEquals("Mešovita", info.preskoceno().get(0).naziv());
        assertEquals(prezentacijaPre, broj("select count(*) from prezentacije where predmet_id = ?", predmet.getId()));
        assertEquals(slajdovaPre, broj("select count(*) from slajdovi sl join prezentacije p on p.id = sl.prezentacija_id"
                + " where p.predmet_id = ?", predmet.getId()));
        assertEquals(List.of(s.n1(), s.k1(), s.n2(), s.k2()), redosled(s.tudja()));
        assertEquals(List.of(s.k4(), s.dk4(), s.nm()), redosled(s.mesovita()));
    }

    @Test
    void ponistiSerijuBriseNeizvedenoISlajdoveUTudjimPrezentacijama() {
        Scenario s = scenario();
        Long pitanjeK1 = jdbc.queryForObject("select pitanje_id from slajdovi where id = ?", Long.class, s.k1());
        Long pitanjeK0 = jdbc.queryForObject("select pitanje_id from slajdovi where prezentacija_id = ? and pitanje_id is not null",
                Long.class, s.neizvedena());

        PonistavanjeInfo info = service.ponistiSeriju(serija, true);

        assertTrue(info.potvrdjeno());
        assertEquals(ocekivanoObrisano(s), info.obrisano().stream().map(PrezentacijaServiceMcpDbTest::kratko).toList());
        assertEquals(ocekivanoPreskoceno(s), info.preskoceno().stream().map(PrezentacijaServiceMcpDbTest::kratko).toList());
        assertTrue(info.obrisano().stream().allMatch(st -> st.razlog() == null));

        // neizvedena prezentacija samo sa slajdovima serije nestaje cela (i pitanje)
        assertEquals(0, broj("select count(*) from prezentacije where id = ?", s.neizvedena()));
        assertEquals(0, broj("select count(*) from slajdovi where prezentacija_id = ?", s.neizvedena()));
        assertEquals(0, broj("select count(*) from pitanja where id = ?", pitanjeK0));
        // prezentacija serije sa Novicinim slajdom i Novicinom kopijom ostaje: briše se samo slajd serije, ostali prenumerisani
        assertEquals(1, broj("select count(*) from prezentacije where id = ?", s.mesovita()));
        assertEquals(List.of(s.dk4(), s.nm()), redosled(s.mesovita()));
        assertNull(serijaSlajda(s.dk4()));
        // slajd serije u tuđoj prezentaciji obrisan sa pitanjem, ostali prenumerisani, druga serija netaknuta
        assertEquals(List.of(s.n1(), s.n2(), s.k2()), redosled(s.tudja()));
        assertEquals(0, broj("select count(*) from pitanja where id = ?", pitanjeK1));
        assertEquals(0, broj("select count(*) from pitanje_opcije where pitanje_id = ?", pitanjeK1));
        // izvedena i one koje se izvode ostaju netaknute
        assertEquals(1, redosled(s.izvedena()).size());
        assertEquals(2, redosled(s.izvodiSe()).size());
        assertEquals(serija, serijaSlajda(s.k3()));
        assertEquals(1, redosled(s.serijaKojaSeIzvodi()).size());

        // ponovo: ostaju samo preskočene stavke
        PonistavanjeInfo opet = service.ponistiSeriju(serija, true);
        assertEquals(List.of(), opet.obrisano());
        assertEquals(ocekivanoPreskoceno(s), opet.preskoceno().stream().map(PrezentacijaServiceMcpDbTest::kratko).toList());
    }

    @Test
    void ponistiSerijuBezStavkiIneispravnaSerija() {
        PonistavanjeInfo info = service.ponistiSeriju("nepostojeca-" + serija, true);
        assertTrue(info.potvrdjeno());
        assertEquals(List.of(), info.obrisano());
        assertEquals(List.of(), info.preskoceno());
        greska(400, Serija.PORUKA, () -> service.ponistiSeriju(null, false));
        greska(400, Serija.PORUKA, () -> service.ponistiSeriju("S-1", true));
    }
}
