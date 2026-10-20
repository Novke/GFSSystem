package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slajd ZADATAK u {@link PrezentacijaService} nad pravom MySQL bazom (Flyway, kao CI), JDBC fixture u PREDAVANJE stablu:
 * koren K -> L (list, 3 AKTIVNE sa SVG-om), P (list, bez aktivnih), I (list, 2 AKTIVNE, isključena tema); uz to TEST grupa
 * istog predmeta i grupa drugog predmeta. Proverava čuvanje grupe, tajmera i broja varijanti, granice grupe (isti predmet,
 * namena PREDAVANJE), opis sa upozorenjima, dupliranje, prelazak u drugi tip, obrisanu grupu i brojač "pitanja". Bez
 * {@code @Transactional}: svaki poziv servisa se zaista commit-uje, pa test čisti za sobom.
 */
@SpringBootTest
class PrezentacijaZadatakDbTest {

    static final String SEMA_L = """
            [{"kljuc":"a","oznaka":"a)","tip":"BROJ","odstupanje":0.5,"odstupanjeTip":"APSOLUTNO","jedinica":"cm"}]""";
    static final String SEMA_I = """
            [{"kljuc":"y","oznaka":"y)","tip":"TEKST","zanemariVelicinu":true}]""";
    static final String DRUGI_PREDMET_ILI_TEST = "Grupa mora biti iz banke ovog predmeta, namena Predavanja.";
    static final String NIJE_PRONADJENA = "Grupa zadataka nije pronađena.";
    static final String BEZ_AKTIVNIH = "Grupa nema aktivnih varijanti; odobri nacrte u banci pre časa.";
    static final String OBRISANA = "Grupa zadataka je obrisana; izaberi drugu.";

    @Autowired PrezentacijaService service;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JdbcTemplate jdbc;

    Predmet predmet;
    Predmet drugiPredmet;
    long k, l, p, i, test, tudja;
    final List<Long> prezentacije = new ArrayList<>();
    final List<String> mediji = new ArrayList<>();

    @BeforeEach
    void setUp() {
        predmet = noviPredmet();
        drugiPredmet = noviPredmet();
        k = grupa(predmet, null, "PREDAVANJE", "K", 1, "[]");
        l = grupa(predmet, k, "PREDAVANJE", "L", 1, SEMA_L);
        p = grupa(predmet, k, "PREDAVANJE", "P", 2, "[]");
        i = grupa(predmet, k, "PREDAVANJE", "I", 3, SEMA_I);
        test = grupa(predmet, null, "TEST", "T", 1, "[]");
        tudja = grupa(drugiPredmet, null, "PREDAVANJE", "Tuđa", 1, "[]");
        for (int n = 1; n <= 3; n++) {
            varijanta(l, "L" + n);
        }
        varijanta(i, "I1");
        varijanta(i, "I2");
        jdbc.update("insert into iskljucene_teme (predmet_id, opis, grupa_id, izvor, kreirano) values (?, ?, ?, 'RUCNO', now(6))",
                predmet.getId(), "Podgrupa I", i);
    }

    @AfterEach
    void tearDown() {
        for (Long id : prezentacije) {
            try {
                service.obrisi(id);
            } catch (SystemException e) {
                // već obrisana u testu
            }
        }
        for (Predmet pr : List.of(predmet, drugiPredmet)) {
            jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)", pr.getId());
            jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", pr.getId());
            jdbc.update("delete from grupe_zadataka where predmet_id = ?", pr.getId());
            predmetRepository.deleteById(pr.getId());
        }
        for (String id : mediji) {
            jdbc.update("delete from mediji where id = ?", id);
        }
    }

    Predmet noviPredmet() {
        Predmet pr = new Predmet();
        pr.setNaziv("Prezentacija zadatak DB test " + UUID.randomUUID().toString().substring(0, 8));
        return predmetRepository.save(pr);
    }

    long grupa(Predmet pr, Long roditelj, String namena, String naziv, int rb, String polja) {
        Map<String, Object> red = new HashMap<>();
        red.put("predmet_id", pr.getId());
        red.put("roditelj_id", roditelj);
        red.put("namena", namena);
        red.put("naziv", naziv);
        red.put("rb", rb);
        red.put("tezina", roditelj == null ? null : 3);
        red.put("polja", polja);
        red.put("izvor", "RUCNO");
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        return new SimpleJdbcInsert(jdbc).withTableName("grupe_zadataka").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(red).longValue();
    }

    /** AKTIVNA varijanta sa SVG-om. */
    void varijanta(long grupa, String naziv) {
        String svgId = UUID.randomUUID().toString();
        jdbc.update("insert into mediji (id, naziv, mime, velicina, vrsta, kreirano) "
                + "values (?, 'render.svg', 'image/svg+xml', 10, 'RENDER_SVG', now(6))", svgId);
        mediji.add(svgId);
        Map<String, Object> red = new HashMap<>();
        red.put("grupa_id", grupa);
        red.put("rb", 1);
        red.put("naziv", naziv);
        red.put("tekst", "Zadatak " + naziv);
        red.put("odgovori", "{\"a\":{\"broj\":45.0}}");
        red.put("status", "AKTIVNA");
        red.put("izvor", "RUCNO");
        red.put("ikad_odobrena", true);
        red.put("render_status", "OK");
        red.put("render_svg_id", svgId);
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        new SimpleJdbcInsert(jdbc).withTableName("varijante").usingGeneratedKeyColumns("id").executeAndReturnKey(red);
    }

    Long nova(String naziv) {
        Long id = service.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), naziv, null, null)).id();
        prezentacije.add(id);
        return id;
    }

    static SlajdCmd zadatak(long grupaId, Integer vreme, Integer broj) {
        return new SlajdCmd(TipSlajda.ZADATAK, null, null, null, null, false, null, null, null,
                new ZadatakSlajda(grupaId, vreme, broj));
    }

    static SlajdCmd info(String naslov) {
        return new SlajdCmd(TipSlajda.INFO, naslov, null, null, null, false, null, null, null);
    }

    static List<GrupaPutanja> putanja(Object... parovi) {
        List<GrupaPutanja> rezultat = new ArrayList<>();
        for (int n = 0; n < parovi.length; n += 2) {
            rezultat.add(new GrupaPutanja((Long) parovi[n], (String) parovi[n + 1]));
        }
        return rezultat;
    }

    static void greska(int kod, String poruka, Executable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv);
        assertEquals(kod, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    long broj(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- opis i upozorenja

    @Test
    void zadatakNadListomImaOpisSemuIBrojKandidata() {
        Long id = nova("Zadaci");

        SlajdDetails d = service.dodajSlajd(id, zadatak(l, 120, 2), null);

        assertEquals(TipSlajda.ZADATAK, d.tip());
        ZadatakSlajdaInfo z = d.zadatak();
        assertEquals(l, z.grupaId());
        assertEquals(120, z.vremeSekunde());
        assertEquals(2, z.brojVarijanti());
        assertEquals("L", z.grupaNaziv());
        assertEquals(putanja(k, "K", l, "L"), z.putanja());
        assertTrue(z.list());
        assertEquals(3, z.brojAktivnihVarijanti());
        assertEquals(1, z.polja().size());
        assertEquals("a", z.polja().get(0).kljuc());
        assertEquals(TipPolja.BROJ, z.polja().get(0).tip());
        assertFalse(z.iskljucena());
        assertEquals(List.of(), z.iskljucenePodgrupe());
        assertEquals(List.of(), z.upozorenja());
        assertNull(d.pitanje());
        assertNull(d.naslov());
    }

    @Test
    void listBezAktivnihDajeUpozorenje() {
        SlajdDetails d = service.dodajSlajd(nova("Zadaci"), zadatak(p, null, null), null);

        assertEquals(0, d.zadatak().brojAktivnihVarijanti());
        assertEquals(List.of(BEZ_AKTIVNIH), d.zadatak().upozorenja());
        assertNull(d.zadatak().vremeSekunde());
        assertNull(d.zadatak().brojVarijanti());
    }

    @Test
    void iskljucenaTemaDajeUpozorenje() {
        SlajdDetails d = service.dodajSlajd(nova("Zadaci"), zadatak(i, null, null), null);

        assertTrue(d.zadatak().iskljucena());
        assertEquals(2, d.zadatak().brojAktivnihVarijanti(), "isključen list ostaje izvlačiv: samo upozorenje");
        assertEquals(List.of("Tema je isključena."), d.zadatak().upozorenja());
    }

    @Test
    void korenSaIskljucenomPodgrupomDajeUpozorenjeBezSeme() {
        SlajdDetails d = service.dodajSlajd(nova("Zadaci"), zadatak(k, null, 4), null);

        ZadatakSlajdaInfo z = d.zadatak();
        assertEquals("K", z.grupaNaziv());
        assertFalse(z.list());
        assertNull(z.polja());
        assertEquals(3, z.brojAktivnihVarijanti(), "L 3 + P 0, bez isključene I");
        assertEquals(putanja(i, "I"), z.iskljucenePodgrupe());
        assertEquals(List.of("Isključene podgrupe se ne izvlače: I."), z.upozorenja());
        assertEquals(4, z.brojVarijanti());
    }

    @Test
    void upozorenjaIduRedomIzDogovora() {
        // P je list bez aktivnih; isključimo ga: "Tema je isključena." pre "Grupa nema aktivnih varijanti"
        jdbc.update("insert into iskljucene_teme (predmet_id, opis, grupa_id, izvor, kreirano) values (?, ?, ?, 'RUCNO', now(6))",
                predmet.getId(), "Podgrupa P", p);

        SlajdDetails d = service.dodajSlajd(nova("Zadaci"), zadatak(p, null, null), null);

        assertEquals(List.of("Tema je isključena.", BEZ_AKTIVNIH), d.zadatak().upozorenja());
    }

    // ---------------------------------------------------------------- grupa mora biti iz banke ovog predmeta

    @Test
    void grupaTestaDrugogPredmetaINepostojecaSu400() {
        Long id = nova("Zadaci");
        long pre = broj("select count(*) from slajdovi where prezentacija_id = ?", id);

        greska(400, DRUGI_PREDMET_ILI_TEST, () -> service.dodajSlajd(id, zadatak(test, null, null), null));
        greska(400, DRUGI_PREDMET_ILI_TEST, () -> service.dodajSlajd(id, zadatak(tudja, null, null), null));
        greska(400, NIJE_PRONADJENA, () -> service.dodajSlajd(id, zadatak(Long.MAX_VALUE - 1, null, null), null));

        assertEquals(pre, broj("select count(*) from slajdovi where prezentacija_id = ?", id));
    }

    @Test
    void izmenaNaTuduGrupuJe400IStariSadrzajOstaje() {
        Long id = nova("Zadaci");
        SlajdDetails d = service.dodajSlajd(id, zadatak(l, 60, 2), null);

        greska(400, DRUGI_PREDMET_ILI_TEST, () -> service.izmeniSlajd(d.id(), zadatak(tudja, 90, 3)));
        greska(400, NIJE_PRONADJENA, () -> service.izmeniSlajd(d.id(), zadatak(Long.MAX_VALUE - 1, 90, 3)));

        assertEquals(l, jdbc.queryForObject("select grupa_zadataka_id from slajdovi where id = ?", Long.class, d.id()));
        assertEquals(60, jdbc.queryForObject("select zadatak_vreme_sekunde from slajdovi where id = ?", Integer.class, d.id()));
        SlajdDetails izmenjen = service.izmeniSlajd(d.id(), zadatak(k, 90, 3));
        assertEquals(k, izmenjen.zadatak().grupaId());
        assertEquals(90, izmenjen.zadatak().vremeSekunde());
    }

    @Test
    void zadatakBezGrupeJe400IzPravila() {
        Long id = nova("Zadaci");
        greska(400, "Zadatak slajd mora imati grupu zadataka.", () -> service.dodajSlajd(id, new SlajdCmd(
                TipSlajda.ZADATAK, null, null, null, null, false, null, null, null, null), null));
        greska(400, "Broj različitih zadataka mora biti od 1 do 50.", () -> service.dodajSlajd(id, zadatak(l, null, 51), null));
        greska(400, "Vreme za odgovor mora biti od 5 do 600 sekundi.", () -> service.dodajSlajd(id, zadatak(l, 4, null), null));
    }

    @Test
    void uvozProveravaGrupuSaRednimBrojemSlajda() {
        Long id = nova("Uvoz");

        greska(400, "Slajd 2: " + DRUGI_PREDMET_ILI_TEST, () -> service.uveziSlajdove(id,
                new UvozSlajdovaCmd(null, List.of(info("A"), zadatak(tudja, null, null), zadatak(l, null, null)))));
        greska(400, "Slajd 1: " + NIJE_PRONADJENA, () -> service.uveziSlajdove(id,
                new UvozSlajdovaCmd(null, List.of(zadatak(Long.MAX_VALUE - 1, null, null)))));
        assertEquals(0, broj("select count(*) from slajdovi where prezentacija_id = ?", id), "ništa nije upisano");

        PrezentacijaDetails d = service.uveziSlajdove(id,
                new UvozSlajdovaCmd(null, List.of(info("A"), zadatak(l, 30, 1), zadatak(k, null, null))));
        assertEquals(3, d.brojSlajdova());
        assertEquals(2, d.brojPitanja(), "ZADATAK slajdovi se broje kao slajdovi sa rundama");
        assertEquals(l, d.slajdovi().get(1).zadatak().grupaId());
        assertEquals(30, d.slajdovi().get(1).zadatak().vremeSekunde());
        assertEquals("K", d.slajdovi().get(2).zadatak().grupaNaziv());
        assertNull(d.slajdovi().get(0).zadatak());
    }

    @Test
    void slajdIzSerijeProveravaGrupu() {
        Long id = nova("Klod");

        greska(400, DRUGI_PREDMET_ILI_TEST, () -> service.dodajSlajdIzSerije(id, zadatak(tudja, null, null), null, "serija-zadatak"));
        SlajdDetails d = service.dodajSlajdIzSerije(id, zadatak(l, 120, 2), null, "serija-zadatak");

        assertEquals(l, d.zadatak().grupaId());
        assertEquals("serija-zadatak", jdbc.queryForObject("select serija from slajdovi where id = ?", String.class, d.id()));
    }

    // ---------------------------------------------------------------- dupliranje, izmena tipa, brisanje grupe, brojač

    @Test
    void dupliranjeSlajdaIPrezentacijeKopirajeGrupuVremeIBroj() {
        Long id = nova("Zadaci");
        SlajdDetails original = service.dodajSlajd(id, zadatak(l, 120, 3), null);

        SlajdDetails kopija = service.duplirajSlajd(original.id());
        assertNotEquals(original.id(), kopija.id());
        assertEquals(l, kopija.zadatak().grupaId());
        assertEquals(120, kopija.zadatak().vremeSekunde());
        assertEquals(3, kopija.zadatak().brojVarijanti());
        assertEquals("L", kopija.zadatak().grupaNaziv());

        PrezentacijaDetails dupla = service.dupliraj(id);
        prezentacije.add(dupla.id());
        assertEquals(2, dupla.brojSlajdova());
        for (SlajdDetails s : dupla.slajdovi()) {
            assertEquals(TipSlajda.ZADATAK, s.tip());
            assertEquals(l, s.zadatak().grupaId());
            assertEquals(120, s.zadatak().vremeSekunde());
            assertEquals(3, s.zadatak().brojVarijanti());
        }
        assertEquals(2, dupla.brojPitanja());
    }

    @Test
    void izmenaUInfoBrisePodatkeZadatka() {
        Long id = nova("Zadaci");
        SlajdDetails d = service.dodajSlajd(id, zadatak(l, 120, 3), null);

        SlajdDetails info = service.izmeniSlajd(d.id(), info("Pauza"));

        assertEquals(TipSlajda.INFO, info.tip());
        assertNull(info.zadatak());
        Map<String, Object> red = jdbc.queryForMap("select grupa_zadataka_id, zadatak_vreme_sekunde, zadatak_broj_varijanti "
                + "from slajdovi where id = ?", d.id());
        assertNull(red.get("grupa_zadataka_id"));
        assertNull(red.get("zadatak_vreme_sekunde"));
        assertNull(red.get("zadatak_broj_varijanti"));

        // i obrnuto: INFO postaje ZADATAK, naslov se gubi
        SlajdDetails opet = service.izmeniSlajd(d.id(), zadatak(l, null, null));
        assertEquals(TipSlajda.ZADATAK, opet.tip());
        assertNull(opet.naslov());
        assertEquals(l, opet.zadatak().grupaId());
    }

    @Test
    void obrisanaGrupaOstavljaSlajdSaJedinimUpozorenjem() {
        Long id = nova("Zadaci");
        SlajdDetails d = service.dodajSlajd(id, zadatak(l, 120, 3), null);
        jdbc.update("delete from varijante where grupa_id = ?", l);
        jdbc.update("delete from grupe_zadataka where id = ?", l);

        SlajdDetails citanje = service.detalji(id).slajdovi().get(0);

        assertEquals(d.id(), citanje.id());
        assertEquals(TipSlajda.ZADATAK, citanje.tip());
        assertNull(citanje.zadatak().grupaId());
        assertNull(citanje.zadatak().grupaNaziv());
        assertEquals(120, citanje.zadatak().vremeSekunde());
        assertEquals(3, citanje.zadatak().brojVarijanti());
        assertEquals(List.of(OBRISANA), citanje.zadatak().upozorenja());
        assertEquals(0, citanje.zadatak().brojAktivnihVarijanti());
        assertEquals(List.of(), citanje.zadatak().putanja());
        assertEquals(OBRISANA, service.slajd(d.id()).zadatak().upozorenja().get(0));

        // slajd se može sačuvati samo sa novom grupom
        greska(400, "Zadatak slajd mora imati grupu zadataka.", () -> service.izmeniSlajd(d.id(), new SlajdCmd(
                TipSlajda.ZADATAK, null, null, null, null, false, null, null, null, new ZadatakSlajda(null, 120, 3))));
        assertEquals(k, service.izmeniSlajd(d.id(), zadatak(k, 120, 3)).zadatak().grupaId());
    }

    @Test
    void brojPitanjaBrojiZadatakSlajdove() {
        Long id = nova("Zadaci");
        service.dodajSlajd(id, info("A"), null);
        service.dodajSlajd(id, zadatak(l, null, null), null);

        assertEquals(1, broj("select count(*) from slajdovi where prezentacija_id = ? and tip = 'ZADATAK'", id));
        PrezentacijaInfo info = service.lista(predmet.getId()).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
        assertEquals(2, info.brojSlajdova());
        assertEquals(1, info.brojPitanja());
        assertEquals(1, service.detalji(id).brojPitanja());
        assertNull(service.detalji(id).slajdovi().get(0).zadatak());
        assertEquals("L", service.detalji(id).slajdovi().get(1).zadatak().grupaNaziv());
    }

    @Test
    void opisVidiStanjeBankeUTrenutkuCitanja() {
        Long id = nova("Zadaci");
        SlajdDetails d = service.dodajSlajd(id, zadatak(p, null, null), null);
        assertEquals(List.of(BEZ_AKTIVNIH), d.zadatak().upozorenja());

        varijanta(p, "P1");

        assertEquals(1, service.slajd(d.id()).zadatak().brojAktivnihVarijanti());
        assertEquals(List.of(), service.slajd(d.id()).zadatak().upozorenja());
    }
}
