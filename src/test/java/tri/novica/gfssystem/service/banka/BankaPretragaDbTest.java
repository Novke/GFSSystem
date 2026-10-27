package tri.novica.gfssystem.service.banka;

import tri.novica.gfssystem.exceptions.SystemException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.banka.BankaFilter;
import tri.novica.gfssystem.dto.banka.BankaPretragaStavka;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.utility.PageableUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BankaPretragaService} nad pravom MySQL bazom (Flyway V1..V11, kao CI): filteri i {@code LIKE} sa escape-om,
 * podrazumevani sort i stranice, putanja grupe i broj SQL upita (bez N+1 po varijanti i po nivou stabla). Bez
 * {@code @Transactional}: svaki poziv se zaista commit-uje, pa test čisti za sobom kroz JDBC.
 */
@SpringBootTest
class BankaPretragaDbTest {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 17, 12, 0);
    static final PageRequest PRVIH_25 = PageRequest.of(0, 25, Sort.by(Sort.Order.desc("izmenjeno"), Sort.Order.desc("id")));

    @Autowired BankaPretragaService service;
    @Autowired GrupaZadatakaRepository grupaRepository;
    @Autowired VarijantaRepository varijantaRepository;
    @Autowired PredmetRepository predmetRepository;
    @Autowired MedijRepository medijRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;

    Predmet predmet;
    Predmet drugiPredmet;
    final List<String> mediji = new ArrayList<>();
    GrupaZadataka brojniSistemi;   // PREDAVANJE, koren
    GrupaZadataka konverzije;      // PREDAVANJE, list ispod "Brojni sistemi"
    GrupaZadataka automati;        // TEST, koren koji je i list
    Varijanta v1;   // Konverzije, nacrt, najstarija
    Varijanta v2;   // Konverzije, aktivna, bez naziva
    Varijanta v3;   // Automati, nacrt, najnovija

    @BeforeEach
    void setUp() {
        predmet = predmet("Banka pretraga");
        drugiPredmet = predmet("Banka pretraga, drugi");
        brojniSistemi = grupa(predmet, null, Namena.PREDAVANJE, "Brojni sistemi", null);
        konverzije = grupa(predmet, brojniSistemi, Namena.PREDAVANJE, "Konverzije", 2);
        automati = grupa(predmet, null, Namena.TEST, "Konačni automati", 3);
        v1 = varijanta(konverzije, "Binarni u dekadni", "Pretvori broj 1011 u dekadni sistem.\r\n\r\n   Pokaži postupak.",
                StatusVarijante.NACRT, T0);
        v2 = varijanta(konverzije, null, "Heksadecimalni zapis broja 255", StatusVarijante.AKTIVNA, T0.plusMinutes(1));
        v3 = varijanta(automati, "DKA za paran broj nula", "Nacrtaj automat nad {0,1}.", StatusVarijante.NACRT, T0.plusMinutes(2));
    }

    @AfterEach
    void tearDown() {
        for (Predmet p : List.of(predmet, drugiPredmet)) {
            String grupe = "(select id from grupe_zadataka where predmet_id = ?)";
            jdbc.update("delete from varijante where grupa_id in " + grupe, p.getId());
            jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", p.getId());
            jdbc.update("delete from grupe_zadataka where predmet_id = ?", p.getId());
            predmetRepository.deleteById(p.getId());
        }
        for (String id : mediji) jdbc.update("delete from mediji where id = ?", id);
    }

    Predmet predmet(String naziv) {
        Predmet p = new Predmet();
        p.setNaziv(naziv + " " + UUID.randomUUID().toString().substring(0, 8));
        return predmetRepository.save(p);
    }

    GrupaZadataka grupa(Predmet p, GrupaZadataka roditelj, Namena namena, String naziv, Integer tezina) {
        GrupaZadataka g = new GrupaZadataka();
        g.setPredmet(p);
        g.setRoditelj(roditelj);
        g.setNamena(namena);
        g.setNaziv(naziv);
        g.setRb(1);
        g.setTezina(tezina);
        g.setKreirano(T0);
        g.setIzmenjeno(T0);
        return grupaRepository.save(g);
    }

    Varijanta varijanta(GrupaZadataka g, String naziv, String tekst, StatusVarijante status, LocalDateTime izmenjeno) {
        Varijanta v = new Varijanta();
        v.setGrupa(g);
        v.setRb((int) varijantaRepository.countByGrupaId(g.getId()) + 1);
        v.setNaziv(naziv);
        v.setTekst(tekst);
        v.setStatus(status);
        v.setIzvor(IzvorVarijante.RUCNO);
        v.setRenderStatus(status == StatusVarijante.AKTIVNA ? RenderStatus.OK : RenderStatus.CEKA);
        v.setKreirano(T0);
        v.setIzmenjeno(izmenjeno);
        return varijantaRepository.save(v);
    }

    BankaFilter filter(String q) {
        return new BankaFilter(predmet.getId(), null, q, null);
    }

    List<Long> ids(PagedModel<BankaPretragaStavka> strana) {
        return strana.getContent().stream().map(BankaPretragaStavka::varijantaId).toList();
    }

    @Test
    void bezFilteraSveVarijantePredmetaNajnovijePrve() {
        PagedModel<BankaPretragaStavka> strana = service.pretraga(filter(null), PRVIH_25);

        assertEquals(List.of(v3.getId(), v2.getId(), v1.getId()), ids(strana));
        assertEquals(3, strana.getMetadata().totalElements());
    }

    @Test
    void varijanteDrugogPredmetaSeNeVide() {
        GrupaZadataka tudja = grupa(drugiPredmet, null, Namena.PREDAVANJE, "Binarni u dekadni", 1);
        Varijanta tudjaVarijanta = varijanta(tudja, "Binarni u dekadni", "Pretvori broj 1011 u dekadni sistem.",
                StatusVarijante.AKTIVNA, T0.plusHours(1));

        assertEquals(List.of(v3.getId(), v2.getId(), v1.getId()), ids(service.pretraga(filter(null), PRVIH_25)));
        assertEquals(List.of(tudjaVarijanta.getId()),
                ids(service.pretraga(new BankaFilter(drugiPredmet.getId(), null, "dekadni", null), PRVIH_25)));
        // bez predmeta se vidi sve
        assertTrue(ids(service.pretraga(new BankaFilter(null, null, "dekadni", null), PRVIH_25)).contains(tudjaVarijanta.getId()));
    }

    @Test
    void upitPogadjaTekstNazivVarijanteINazivGrupe() {
        // tekst (v2) i bez obzira na velika slova
        assertEquals(List.of(v2.getId()), ids(service.pretraga(filter("HEKSADECIMALNI"), PRVIH_25)));
        assertEquals(List.of(v2.getId()), ids(service.pretraga(filter(" 255 "), PRVIH_25)));
        // naziv varijante (v3), a tekst ga ne sadrži
        assertEquals(List.of(v3.getId()), ids(service.pretraga(filter("paran broj"), PRVIH_25)));
        // naziv grupe: obe varijante "Konverzije", ni jedna im naziv ni tekst ne sadrži tu reč
        assertEquals(List.of(v2.getId(), v1.getId()), ids(service.pretraga(filter("konverzij"), PRVIH_25)));
        // naziv roditeljske grupe se ne pretražuje: samo grupa u kojoj je varijanta
        assertEquals(List.of(), ids(service.pretraga(filter("brojni sistemi"), PRVIH_25)));
        // i naziv i tekst varijante sadrže upit: jedan red, ne dva
        assertEquals(List.of(v1.getId()), ids(service.pretraga(filter("dekadni"), PRVIH_25)));
    }

    @Test
    void dzokeriUUpituSuDoslovni() {
        assertEquals(List.of(), ids(service.pretraga(filter("1_1"), PRVIH_25)));     // _ nije "bilo koji znak" (1011)
        assertEquals(List.of(), ids(service.pretraga(filter("%%"), PRVIH_25)));      // % nije "sve"
        assertEquals(List.of(v3.getId()), ids(service.pretraga(filter("{0,1}"), PRVIH_25)));
    }

    @Test
    void namenaIStatusSuzavajuRezultat() {
        assertEquals(List.of(v3.getId()),
                ids(service.pretraga(new BankaFilter(predmet.getId(), Namena.TEST, null, null), PRVIH_25)));
        assertEquals(List.of(v2.getId(), v1.getId()),
                ids(service.pretraga(new BankaFilter(predmet.getId(), Namena.PREDAVANJE, null, null), PRVIH_25)));
        assertEquals(List.of(v2.getId()),
                ids(service.pretraga(new BankaFilter(predmet.getId(), null, null, StatusVarijante.AKTIVNA), PRVIH_25)));
        assertEquals(List.of(v3.getId(), v1.getId()),
                ids(service.pretraga(new BankaFilter(predmet.getId(), null, null, StatusVarijante.NACRT), PRVIH_25)));
        assertEquals(List.of(),
                ids(service.pretraga(new BankaFilter(predmet.getId(), Namena.TEST, null, StatusVarijante.AKTIVNA), PRVIH_25)));
    }

    @Test
    void stranicenjeIBrojaciUkupnog() {
        PagedModel<BankaPretragaStavka> prva = service.pretraga(filter(null), PageRequest.of(0, 2, PRVIH_25.getSort()));
        PagedModel<BankaPretragaStavka> druga = service.pretraga(filter(null), PageRequest.of(1, 2, PRVIH_25.getSort()));

        assertEquals(List.of(v3.getId(), v2.getId()), ids(prva));
        assertEquals(List.of(v1.getId()), ids(druga));
        assertEquals(3, prva.getMetadata().totalElements());
        assertEquals(2, prva.getMetadata().totalPages());
        assertEquals(2, prva.getMetadata().size());
        assertEquals(1, druga.getMetadata().number());
    }

    @Test
    void sortPoNazivuIPodrazumevaniSortSaIdPoslednji() {
        // sort kroz pravi PageableUtil, kao u REST sloju: id desc je tiebreaker
        var poNazivu = PageableUtil.proveri(PageRequest.of(0, 25, Sort.by("naziv")), BankaPretragaService.SORT_POLJA,
                BankaPretragaService.PODRAZUMEVANI_SORT);
        // MySQL: null naziv prvi pa abecedno
        assertEquals(List.of(v2.getId(), v1.getId(), v3.getId()), ids(service.pretraga(filter(null), poNazivu)));

        var podrazumevano = PageableUtil.proveri(PageRequest.of(0, 25), BankaPretragaService.SORT_POLJA,
                BankaPretragaService.PODRAZUMEVANI_SORT);
        assertEquals(List.of(v3.getId(), v2.getId(), v1.getId()), ids(service.pretraga(filter(null), podrazumevano)));
        assertEquals(Set.of("izmenjeno", "naziv", "nivo"), BankaPretragaService.SORT_POLJA);

        // isto vreme izmene: id opadajuce
        jdbc.update("update varijante set izmenjeno = ? where id in (?, ?)", T0, v1.getId(), v2.getId());
        assertEquals(List.of(v3.getId(), v2.getId(), v1.getId()), ids(service.pretraga(filter(null), podrazumevano)));
    }

    @Test
    void stavkaImaPutanjuNazivStatusRenderIIsecak() {
        BankaPretragaStavka s1 = service.pretraga(filter("binarni"), PRVIH_25).getContent().get(0);

        assertEquals(v1.getId(), s1.varijantaId());
        assertEquals(konverzije.getId(), s1.grupaId());
        assertEquals("Konverzije", s1.grupaNaziv());
        assertEquals(List.of(new GrupaPutanja(brojniSistemi.getId(), "Brojni sistemi"),
                new GrupaPutanja(konverzije.getId(), "Konverzije")), s1.putanja());
        assertEquals("Binarni u dekadni", s1.naziv());
        assertEquals(StatusVarijante.NACRT, s1.status());
        assertEquals(RenderStatus.CEKA, s1.renderStatus());
        assertEquals("Pretvori broj 1011 u dekadni sistem. Pokaži postupak.", s1.isecak());   // novi redovi su razmaci

        BankaPretragaStavka s2 = service.pretraga(filter("255"), PRVIH_25).getContent().get(0);
        assertNull(s2.naziv());
        assertEquals(RenderStatus.OK, s2.renderStatus());

        BankaPretragaStavka s3 = service.pretraga(filter("DKA"), PRVIH_25).getContent().get(0);
        assertEquals(List.of(new GrupaPutanja(automati.getId(), "Konačni automati")), s3.putanja());
    }

    @Test
    void isecakJeNajviseStoSezdesetZnakovaBezPresecanjaPara() {
        String dugacak = "😀".repeat(200);   // 200 znakova, 400 UTF-16 jedinica
        Varijanta v = varijanta(automati, "Dugačak", dugacak, StatusVarijante.NACRT, T0.plusHours(1));

        BankaPretragaStavka s = service.pretraga(filter("dugačak"), PRVIH_25).getContent().get(0);
        assertEquals(v.getId(), s.varijantaId());
        assertEquals("😀".repeat(160), s.isecak());
    }

    @Test
    void putanjaNajdubljegStablaIBezUpitaPoNivouIVarijanti() {
        // dubina 6 (najveća): koren + 5 nivoa; deset varijanti u listu
        GrupaZadataka g = grupa(predmet, null, Namena.TEST, "N1", null);
        for (int i = 2; i <= 6; i++) {
            g = grupa(predmet, g, Namena.TEST, "N" + i, i == 6 ? 4 : null);
        }
        for (int i = 0; i < 10; i++) {
            varijanta(g, "Duboka " + i, "Zadatak broj " + i, StatusVarijante.NACRT, T0.plusHours(2).plusMinutes(i));
        }
        GrupaZadataka duboka = g;

        Statistics statistika = emf.unwrap(SessionFactory.class).getStatistics();
        boolean bila = statistika.isStatisticsEnabled();
        statistika.setStatisticsEnabled(true);
        try {
            statistika.clear();
            PagedModel<BankaPretragaStavka> strana = service.pretraga(new BankaFilter(predmet.getId(), null, "zadatak broj", null), PRVIH_25);
            long upita = statistika.getPrepareStatementCount();

            assertEquals(10, strana.getContent().size());
            assertEquals(List.of("N1", "N2", "N3", "N4", "N5", "N6"),
                    strana.getContent().get(0).putanja().stream().map(GrupaPutanja::naziv).toList());
            assertEquals(duboka.getId(), strana.getContent().get(0).putanja().get(5).id());
            assertEquals(duboka.getId(), strana.getContent().get(9).grupaId());
            // stranica + broj ukupnog (+ po potrebi predmet): ne zavisi od broja varijanti ni od dubine
            assertTrue(upita >= 1 && upita <= 3, "pretraga: " + upita + " SQL upita");
        } finally {
            statistika.setStatisticsEnabled(bila);
        }
    }

    @Test
    void stranicaSaViseGrupaIMedijaNeDajeNPlusJedan() {
        // 12 korena sa po jednim listom i jednom varijantom sa SVG-om zadatka i rešenja: broj upita ne zavisi od broja redova
        for (int i = 0; i < 12; i++) {
            GrupaZadataka koren = grupa(predmet, null, Namena.PREDAVANJE, "Koren " + i, null);
            GrupaZadataka list = grupa(predmet, koren, Namena.PREDAVANJE, "List " + i, 2);
            Varijanta v = varijanta(list, "V" + i, "Tekst " + i, StatusVarijante.AKTIVNA, T0.plusHours(3).plusMinutes(i));
            v.setRenderSvg(render());
            v.setRenderResenjeSvg(render());
            varijantaRepository.save(v);
        }
        Statistics statistika = emf.unwrap(SessionFactory.class).getStatistics();
        boolean bila = statistika.isStatisticsEnabled();
        statistika.setStatisticsEnabled(true);
        try {
            statistika.clear();
            PagedModel<BankaPretragaStavka> strana = service.pretraga(filter(null), PRVIH_25);
            long upita = statistika.getPrepareStatementCount();

            assertEquals(15, strana.getContent().size());
            assertEquals(List.of("Koren 11", "List 11"),
                    strana.getContent().get(0).putanja().stream().map(GrupaPutanja::naziv).toList());
            assertTrue(upita <= 3, "pretraga: " + upita + " SQL upita");
        } finally {
            statistika.setStatisticsEnabled(bila);
        }
    }

    Medij render() {
        Medij m = new Medij();
        m.setId(UUID.randomUUID().toString());
        m.setNaziv("render.svg");
        m.setMime("image/svg+xml");
        m.setVelicina(42);
        m.setVrsta(VrstaMedija.RENDER_SVG);
        m.setKreirano(T0);
        mediji.add(m.getId());
        return medijRepository.save(m);
    }

    @Test
    void nivoFiltriraISortiraUzStavku() {
        v1.setNivo(1);
        v3.setNivo(3);
        varijantaRepository.saveAll(List.of(v1, v3));   // v2 ostaje na podrazumevanom 2

        assertEquals(List.of(v3.getId()),
                ids(service.pretraga(new BankaFilter(predmet.getId(), null, null, null, null, null, 3), PRVIH_25)));
        assertEquals(List.of(v2.getId()),
                ids(service.pretraga(new BankaFilter(predmet.getId(), null, null, null, null, null, 2), PRVIH_25)));
        PagedModel<BankaPretragaStavka> poNivou = service.pretraga(filter(null),
                PageRequest.of(0, 25, Sort.by(Sort.Order.asc("nivo"), Sort.Order.desc("id"))));
        assertEquals(List.of(v1.getId(), v2.getId(), v3.getId()), ids(poNivou));
        assertEquals(List.of(1, 2, 3), poNivou.getContent().stream().map(BankaPretragaStavka::nivo).toList());
        SystemException e = assertThrows(SystemException.class,
                () -> new BankaFilter(predmet.getId(), null, null, null, null, null, 4));
        assertEquals("Nivo mora biti od 1 do 3.", e.getMessage());
    }
}
