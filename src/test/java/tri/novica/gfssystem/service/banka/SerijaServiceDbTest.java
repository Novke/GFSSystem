package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.utility.Serija;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SerijaService} nad pravom MySQL bazom (Flyway V1..V12, kao CI): serije predmeta sa brojačima (nacrti, odobrene,
 * grupe, prezentacije, slajdovi) i vremenom poslednje stavke, najnovije prve; poslednje serije preko svih predmeta;
 * poništavanje u banci (Klodovi neodobreni nacrti, pa CLAUDE grupe koje time ostanu prazne, od najdubljih), bez potvrde
 * isti ishod bez brisanja, a odobrena varijanta, neprazna grupa i Novicine stavke preskočene ili nepomenute. Bez
 * {@code @Transactional}: svaki poziv se zaista commit-uje, pa test čisti za sobom kroz JDBC.
 */
@SpringBootTest
class SerijaServiceDbTest {

    static final String RAZLOG_VARIJANTA = "Odobrena ili nije Klodov nacrt.";
    static final String RAZLOG_GRUPA = "Grupa nije prazna.";

    @Autowired SerijaService service;
    @Autowired GrupaZadatakaService grupaService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired GrupaZadatakaRepository grupaRepository;
    @Autowired VarijantaRepository varijantaRepository;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    Predmet predmet;
    Predmet drugiPredmet;
    /** Jedinstvene po testu: poslednje serije i poništavanje gledaju sve predmete. */
    String oznaka;
    String serija;
    final List<Long> prezentacije = new ArrayList<>();

    /** Glavna serija: Klodov koren sa listom od dva nacrta, Klodov list sa odobrenom varijantom, slajd i prezentacija. */
    GrupaDetails koren;
    GrupaDetails konverzije;
    GrupaDetails odobreni;
    Long nacrt1;
    Long nacrt2;
    Long odobrena;
    Long novicina;
    Long izSerije;
    Long slajd;

    @BeforeEach
    void setUp() {
        oznaka = UUID.randomUUID().toString().substring(0, 8);
        predmet = predmet("Serije DB test ");
        drugiPredmet = predmet("Serije DB test (drugi) ");
        serija = "s-" + oznaka;
    }

    @AfterEach
    void tearDown() {
        for (Long id : prezentacije) {
            try {
                prezentacijaService.obrisi(id);
            } catch (SystemException e) {
                // već obrisana u testu
            }
        }
        for (Predmet p : List.of(predmet, drugiPredmet)) {
            Long id = p.getId();
            String grupePredmeta = "(select id from grupe_zadataka where predmet_id = ?)";
            jdbc.update("update varijante set zamenjuje_id = null where grupa_id in " + grupePredmeta, id);
            jdbc.update("delete from varijante where grupa_id in " + grupePredmeta, id);
            jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", id);
            jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
            predmetRepository.deleteById(id);
        }
    }

    Predmet predmet(String naziv) {
        Predmet p = new Predmet();
        p.setNaziv(naziv + UUID.randomUUID().toString().substring(0, 8));
        return predmetRepository.save(p);
    }

    /** Koren ({@code roditeljId == null}, u {@code namena} predmeta {@code p}) ili podgrupa; CLAUDE sa serijom ili RUCNO. */
    GrupaDetails grupa(Predmet p, Long roditeljId, Namena namena, String naziv, Integer tezina, String serija) {
        GrupaCmd cmd = new GrupaCmd(roditeljId == null ? p.getId() : null, roditeljId, roditeljId == null ? namena : null,
                naziv, null, tezina, null, List.of());
        return serija == null ? grupaService.kreiraj(cmd) : grupaService.kreiraj(cmd, IzvorVarijante.CLAUDE, serija);
    }

    Long varijanta(Long grupaId, String naziv, StatusVarijante status, IzvorVarijante izvor, boolean ikadOdobrena,
                   String serija) {
        Varijanta v = new Varijanta();
        v.setGrupa(grupaRepository.findById(grupaId).orElseThrow());
        v.setRb(varijantaRepository.maxRb(grupaId) + 1);
        v.setNaziv(naziv);
        v.setTekst("Pretvori $1011_2$ u dekadni.");
        v.setStatus(status);
        v.setIzvor(izvor);
        v.setIkadOdobrena(ikadOdobrena);
        v.setSerija(serija);
        v.setRenderStatus(RenderStatus.OK);
        v.setKreirano(LocalDateTime.now(clock));
        v.setIzmenjeno(LocalDateTime.now(clock));
        return varijantaRepository.save(v).getId();
    }

    Long klodovNacrt(Long grupaId, String naziv) {
        return varijanta(grupaId, naziv, StatusVarijante.NACRT, IzvorVarijante.CLAUDE, false, serija);
    }

    Long prezentacija(String naziv, String serija) {
        Long id = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), naziv, null, null), serija).id();
        prezentacije.add(id);
        return id;
    }

    static SlajdCmd info(String naslov) {
        return new SlajdCmd(TipSlajda.INFO, naslov, null, null, null, false, null, null, null);
    }

    /**
     * Glavna serija (vremena zadata, da redosled serija bude određen): koren "Klod koren" (TEST) sa listom "Konverzije"
     * (dva Klodova nacrta), koren "Odobreni" (PREDAVANJE) sa jednom ikad odobrenom AKTIVNOM varijantom serije,
     * prezentacija serije bez slajdova i slajd serije u Novicinoj prezentaciji.
     */
    void glavnaSerija() {
        koren = grupa(predmet, null, Namena.TEST, "Klod koren", null, serija);
        konverzije = grupa(predmet, koren.id(), null, "Konverzije", 2, serija);
        odobreni = grupa(predmet, null, Namena.PREDAVANJE, "Odobreni", 2, serija);
        nacrt1 = klodovNacrt(konverzije.id(), "Kol. 1, zad. 3");
        nacrt2 = klodovNacrt(konverzije.id(), null);
        odobrena = varijanta(odobreni.id(), "Kol. 2, zad. 1", StatusVarijante.AKTIVNA, IzvorVarijante.CLAUDE, true, serija);
        izSerije = prezentacija("Klodova prezentacija", serija);
        novicina = prezentacija("Novicina prezentacija", null);
        slajd = prezentacijaService.dodajSlajdIzSerije(novicina, info("Klodov slajd"), null, serija).id();

        jdbc.update("update grupe_zadataka set kreirano = '2026-10-01 10:00:00' where serija = ?", serija);
        jdbc.update("update varijante set kreirano = '2026-10-02 10:00:00' where serija = ?", serija);
        jdbc.update("update varijante set kreirano = '2026-10-03 11:00:00' where id = ?", nacrt2);
        jdbc.update("update prezentacije set kreirano = '2026-09-01 08:00:00' where id = ?", izSerije);
        // slajdovi nemaju svoje vreme: za njih važi izmena prezentacije (dodavanje slajda je menja)
        jdbc.update("update prezentacije set izmenjeno = '2026-10-04 12:00:00' where id = ?", novicina);
    }

    long broj(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    boolean postojiVarijanta(Long id) {
        return broj("select count(*) from varijante where id = ?", id) == 1;
    }

    boolean postojiGrupa(Long id) {
        return broj("select count(*) from grupe_zadataka where id = ?", id) == 1;
    }

    static StavkaPonistavanja varijantaStavka(Long id, String naziv, String razlog) {
        return new StavkaPonistavanja(StavkaPonistavanja.VARIJANTA, id, naziv, razlog);
    }

    static StavkaPonistavanja grupaStavka(Long id, String naziv, String razlog) {
        return new StavkaPonistavanja(StavkaPonistavanja.GRUPA, id, naziv, razlog);
    }

    static void greska(int kod, String poruka, Executable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv);
        assertEquals(kod, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    @Test
    void serijeVracajuBrojeveINajnovijuPrvu() {
        glavnaSerija();
        String starija = "t-" + oznaka;
        GrupaDetails stara = grupa(predmet, null, Namena.TEST, "Stara serija", null, starija);
        // novija od svake grupe, varijante i prezentacije glavne serije, ali starija od izmene prezentacije njenog slajda
        jdbc.update("update grupe_zadataka set kreirano = '2026-10-03 12:00:00' where id = ?", stara.id());
        String tudja = "u-" + oznaka;
        GrupaDetails uDrugom = grupa(drugiPredmet, null, Namena.TEST, "Drugi predmet", null, tudja);
        jdbc.update("update grupe_zadataka set kreirano = '2026-10-10 09:00:00' where id = ?", uDrugom.id());

        List<SerijaInfo> serije = service.serije(predmet.getId());

        assertEquals(List.of(
                new SerijaInfo(serija, 2, 1, 3, 1, 1, LocalDateTime.of(2026, 10, 4, 12, 0)),
                new SerijaInfo(starija, 0, 0, 1, 0, 0, LocalDateTime.of(2026, 10, 3, 12, 0))), serije);
        assertEquals(List.of(new SerijaInfo(tudja, 0, 0, 1, 0, 0, LocalDateTime.of(2026, 10, 10, 9, 0))),
                service.serije(drugiPredmet.getId()));
    }

    @Test
    void nacrtaBrojiSamoKlodoveNacrteAOdobrenihIkadOdobrene() {
        glavnaSerija();
        // ikad odobrena vraćena u nacrt: nije Klodov neodobren nacrt, ali jeste nacrt i jeste ikad odobrena
        jdbc.update("update varijante set status = 'NACRT' where id = ?", odobrena);
        // varijanta serije koju je napravio Novica (ne može kroz UI, ali kolona to dozvoljava): nije Klodov nacrt
        varijanta(konverzije.id(), null, StatusVarijante.NACRT, IzvorVarijante.RUCNO, false, serija);

        SerijaInfo info = service.serije(predmet.getId()).getFirst();

        assertEquals(serija, info.serija());
        assertEquals(3, info.nacrta());
        assertEquals(1, info.odobrenih());
    }

    @Test
    void poslednjeImaNajviseNNajnovijihPrekoSvihPredmeta() {
        List<String> serije = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            String s = "p" + i + "-" + oznaka;
            serije.add(s);
            GrupaDetails g = grupa(i == 6 ? drugiPredmet : predmet, null, Namena.TEST, "Grupa " + i, null, s);
            // daleka budućnost: ispred svake serije koju su ostavili drugi testovi
            jdbc.update("update grupe_zadataka set kreirano = ? where id = ?", LocalDateTime.of(2099, 1, i, 0, 0), g.id());
        }

        List<SerijaInfo> poslednje = service.poslednje(5);

        assertEquals(List.of(serije.get(5), serije.get(4), serije.get(3), serije.get(2), serije.get(1)),
                poslednje.stream().map(SerijaInfo::serija).toList());
        assertEquals(new SerijaInfo(serije.get(5), 0, 0, 1, 0, 0, LocalDateTime.of(2099, 1, 6, 0, 0)), poslednje.getFirst());
        assertEquals(List.of(), service.poslednje(0));
    }

    @Test
    void ponistiBezPotvrdeSamoRacunaIsteIshode() {
        glavnaSerija();
        long varijanti = broj("select count(*) from varijante where serija = ?", serija);
        long grupa = broj("select count(*) from grupe_zadataka where serija = ?", serija);

        PonistavanjeInfo info = service.ponisti(serija, false);

        assertFalse(info.potvrdjeno());
        assertEquals(List.of(
                varijantaStavka(nacrt1, "Konverzije, varijanta 1: Kol. 1, zad. 3", null),
                varijantaStavka(nacrt2, "Konverzije, varijanta 2", null),
                grupaStavka(konverzije.id(), "Konverzije", null),
                grupaStavka(koren.id(), "Klod koren", null)), info.obrisano());
        assertEquals(List.of(
                varijantaStavka(odobrena, "Odobreni, varijanta 1: Kol. 2, zad. 1", RAZLOG_VARIJANTA),
                grupaStavka(odobreni.id(), "Odobreni", RAZLOG_GRUPA)), info.preskoceno());
        assertEquals(varijanti, broj("select count(*) from varijante where serija = ?", serija));
        assertEquals(grupa, broj("select count(*) from grupe_zadataka where serija = ?", serija));
    }

    @Test
    void ponistiBriseNacrteIPrazneGrupeOdNajdubljih() {
        glavnaSerija();
        PonistavanjeInfo racun = service.ponisti(serija, false);

        PonistavanjeInfo info = service.ponisti(serija, true);

        assertTrue(info.potvrdjeno());
        assertEquals(racun.obrisano(), info.obrisano());
        assertEquals(racun.preskoceno(), info.preskoceno());
        assertFalse(postojiVarijanta(nacrt1));
        assertFalse(postojiVarijanta(nacrt2));
        assertFalse(postojiGrupa(konverzije.id()));
        assertFalse(postojiGrupa(koren.id()));
        assertTrue(postojiVarijanta(odobrena));
        assertTrue(postojiGrupa(odobreni.id()));
        assertEquals("AKTIVNA", jdbc.queryForObject("select status from varijante where id = ?", String.class, odobrena));
        // prezentacije poništava PrezentacijaService (MCP ponisti_seriju spaja oba)
        assertEquals(1, broj("select count(*) from slajdovi where id = ?", slajd));
        assertEquals(1, broj("select count(*) from prezentacije where id = ?", izSerije));

        PonistavanjeInfo ponovo = service.ponisti(serija, true);

        assertEquals(List.of(), ponovo.obrisano());
        assertEquals(info.preskoceno(), ponovo.preskoceno());
    }

    @Test
    void ponistiPreskaceRucnuVarijantuIGrupu() {
        GrupaDetails rucniKoren = grupa(predmet, null, Namena.TEST, "Novicin koren", null, null);
        GrupaDetails klodova = grupa(predmet, rucniKoren.id(), null, "Klodova grupa", 3, serija);
        Long nacrt = klodovNacrt(klodova.id(), "Nacrt");
        Long rucna = varijanta(klodova.id(), "Novicina", StatusVarijante.NACRT, IzvorVarijante.RUCNO, false, null);

        PonistavanjeInfo racun = service.ponisti(serija, false);
        PonistavanjeInfo info = service.ponisti(serija, true);

        for (PonistavanjeInfo i : List.of(racun, info)) {
            assertEquals(List.of(varijantaStavka(nacrt, "Klodova grupa, varijanta 1: Nacrt", null)), i.obrisano());
            assertEquals(List.of(grupaStavka(klodova.id(), "Klodova grupa", RAZLOG_GRUPA)), i.preskoceno());
        }
        assertFalse(postojiVarijanta(nacrt));
        assertTrue(postojiVarijanta(rucna));
        assertTrue(postojiGrupa(klodova.id()));
        assertTrue(postojiGrupa(rucniKoren.id()));
    }

    @Test
    void ponistiNePominjeRucnuGrupuSaSerijomNiTudjuSeriju() {
        // RUCNO grupa sa serijom ne nastaje kroz UI ni MCP, ali je kolona dozvoljava: ne pominje se i ostaje
        GrupaDetails rucna = grupa(predmet, null, Namena.TEST, "Ručna sa serijom", null, null);
        jdbc.update("update grupe_zadataka set serija = ? where id = ?", serija, rucna.id());
        GrupaDetails tudja = grupa(predmet, null, Namena.PREDAVANJE, "Druga serija", 2, "d-" + oznaka);
        Long tudjiNacrt = varijanta(tudja.id(), null, StatusVarijante.NACRT, IzvorVarijante.CLAUDE, false, "d-" + oznaka);

        PonistavanjeInfo info = service.ponisti(serija, true);

        assertEquals(List.of(), info.obrisano());
        assertEquals(List.of(), info.preskoceno());
        assertTrue(postojiGrupa(rucna.id()));
        assertTrue(postojiVarijanta(tudjiNacrt));
    }

    @Test
    void serijaVanObrascaJe400APredmetObavezan() {
        greska(400, Serija.PORUKA, () -> service.ponisti("Uvoz-1", true));
        greska(400, Serija.PORUKA, () -> service.ponisti(null, false));
        greska(400, "Predmet je obavezan.", () -> service.serije(null));
        greska(404, "Predmet nije pronađen.", () -> service.serije(-1L));
    }
}
