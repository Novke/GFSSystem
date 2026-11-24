package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CrtanjeService} nad pravom MySQL bazom (kao CI), bez {@code @Transactional}: strane izvođenja, verzije, granice
 * (404/410/400), bela strana ({@code bela_slajd_id}), crtež u snimku nastavnika, raniji crteži, brisanje sa izvođenjem i
 * tabla predavanja.
 */
@SpringBootTest
class CrtanjeServiceDbTest {

    @Autowired CrtanjeService crtanje;
    @Autowired IzvodjenjeService service;
    @Autowired StanjeService stanjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired GrupaRepository grupaRepository;
    @Autowired PredavanjeRepository predavanjeRepository;
    @Autowired JdbcTemplate jdbc;

    final JsonMapper mapper = JsonMapper.builder().build();
    Predmet predmet;
    Grupa grupa;
    final List<Long> prezentacije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String oznaka = UUID.randomUUID().toString().substring(0, 8);
        predmet = new Predmet();
        predmet.setNaziv("Crtanje DB " + oznaka);
        predmet = predmetRepository.save(predmet);
        grupa = new Grupa();
        grupa.setNaziv("CR-" + oznaka);
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

    PrezentacijaDetails prezentacija(int slajdova) {
        PrezentacijaDetails p = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Crtanje", null, null));
        prezentacije.add(p.id());
        for (int i = 1; i <= slajdova; i++) {
            prezentacijaService.dodajSlajd(p.id(), new SlajdCmd(TipSlajda.INFO, "Slajd " + i, null, null, null, false, null, null, null), null);
        }
        return prezentacijaService.detalji(p.id());
    }

    Long pokreni(PrezentacijaDetails p, boolean cuvanje) {
        return service.pokreni(p.id(), new PokreniCmd(cuvanje, cuvanje ? grupa.getId() : null, null)).id();
    }

    NastavnickoStanje k(Long id, TipKomande tip) {
        return service.komanda(id, new KomandaCmd(tip, null));
    }

    NastavnickoStanje idiNa(Long id, int indeks) {
        return service.komanda(id, new KomandaCmd(TipKomande.IDI_NA, indeks));
    }

    JsonNode telo(String... elementi) {
        return mapper.readTree("{\"elementi\":[" + String.join(",", elementi) + "]}");
    }

    static String linija(String boja) {
        return "{\"tip\":\"LINIJA\",\"boja\":\"" + boja + "\",\"debljina\":3,\"tacke\":[10,20,300,400]}";
    }

    static final String OLOVKA = "{\"tip\":\"OLOVKA\",\"boja\":\"#2456a6\",\"debljina\":6,\"tacke\":[1.04,2,3,4.06,5,6]}";

    long broj(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    static int status(Runnable r) {
        return assertThrows(SystemException.class, r::run).getCode();
    }

    @Test
    void upisCitanjeVerzijaIGranice() {
        PrezentacijaDetails p = prezentacija(2);
        Long s1 = p.slajdovi().get(0).id();
        Long s2 = p.slajdovi().get(1).id();
        Long id = pokreni(p, false);

        assertEquals(new StranaCrteza(List.of(), 0), crtanje.strana(id, s1, PozadinaCrteza.SLAJD));
        assertEquals(1, crtanje.upisi(id, s1, PozadinaCrteza.SLAJD, telo(linija("#c62828"))).verzija());
        assertEquals(2, crtanje.upisi(id, s1, PozadinaCrteza.SLAJD, telo(linija("#c62828"), OLOVKA)).verzija());
        StranaCrteza st = crtanje.strana(id, s1, PozadinaCrteza.SLAJD);
        assertEquals(2, st.verzija());
        assertEquals(2, st.elementi().size());
        assertEquals("#C62828", st.elementi().get(0).boja(), "boja se čuva velikim slovima");
        assertEquals(TipElementa.OLOVKA, st.elementi().get(1).tip());
        assertEquals(List.of(1.0, 2.0, 3.0, 4.1, 5.0, 6.0), st.elementi().get(1).tacke());
        // druga pozadina istog slajda je druga strana
        assertEquals(0, crtanje.strana(id, s1, PozadinaCrteza.BELA).verzija());
        assertEquals(1, crtanje.upisi(id, s2, PozadinaCrteza.BELA, telo(OLOVKA)).verzija());

        // "Obriši sve": red ostaje, verzija raste, pregled ga ne prikazuje
        assertEquals(3, crtanje.upisi(id, s1, PozadinaCrteza.SLAJD, telo()).verzija());
        assertEquals("[]", jdbc.queryForObject(
                "select elementi from crtezi_izvodjenja where izvodjenje_id = ? and slajd_id = ? and pozadina = 'SLAJD'", String.class, id, s1));
        assertEquals(List.of(s2), crtanje.sve(id).stream().map(CrtezIzvodjenjaInfo::slajdId).toList());

        // tuđi slajd, nepostojeće izvođenje, nedostaje pozadina, neispravno telo
        Long tudji = prezentacija(1).slajdovi().get(0).id();
        assertEquals(404, status(() -> crtanje.upisi(id, tudji, PozadinaCrteza.SLAJD, telo(OLOVKA))));
        assertEquals(404, status(() -> crtanje.strana(id, tudji, PozadinaCrteza.SLAJD)));
        assertEquals(404, status(() -> crtanje.ranije(id, tudji)));
        assertEquals(404, status(() -> crtanje.upisi(Long.MAX_VALUE, s1, PozadinaCrteza.SLAJD, telo(OLOVKA))));
        assertEquals(400, status(() -> crtanje.upisi(id, s1, null, telo(OLOVKA))));
        assertEquals(400, status(() -> crtanje.upisi(id, s1, PozadinaCrteza.SLAJD, telo(linija("crvena")))));
        assertEquals(400, status(() -> crtanje.upisi(id, s1, PozadinaCrteza.SLAJD,
                mapper.readTree("{\"elementi\":[],\"nepoznato\":1}"))));
        assertEquals(3, crtanje.strana(id, s1, PozadinaCrteza.SLAJD).verzija(), "odbijen upis ne menja stranu");

        // posle kraja (i bez čuvanja) upis je 410, a crteži ostaju
        k(id, TipKomande.ZAVRSI);
        assertEquals(410, status(() -> crtanje.upisi(id, s2, PozadinaCrteza.BELA, telo(OLOVKA))));
        assertEquals(1, crtanje.sve(id).size());
        assertEquals(1, crtanje.strana(id, s2, PozadinaCrteza.BELA).elementi().size());

        // brisanje izvođenja briše crteže (FK CASCADE)
        service.obrisi(id);
        assertEquals(0, broj("select count(*) from crtezi_izvodjenja where izvodjenje_id = ?", id));
    }

    @Test
    void belaStranaICrtezUSnimku() {
        PrezentacijaDetails p = prezentacija(3);
        Long s1 = p.slajdovi().get(0).id();
        Long s2 = p.slajdovi().get(1).id();
        Long s3 = p.slajdovi().get(2).id();
        Long id = pokreni(p, false);

        // prijava: nema trenutne strane; beli ekran na prijavi još nema slajd
        assertNull(stanjeService.nastavnicko(id).crtez());
        NastavnickoStanje st = k(id, TipKomande.EKRAN_BEO);
        assertEquals(Ekran.BEO, st.ekran());
        assertNull(st.crtez());
        assertNull(jdbc.queryForObject("select bela_slajd_id from izvodjenja where id = ?", Long.class, id));
        // prvi slajd do kog navigacija stigne postaje slajd bele strane, i ostaje dok se lista
        st = k(id, TipKomande.SLEDECI);
        assertEquals(new CrtezTrenutni(s1, PozadinaCrteza.BELA, 0), st.crtez());
        st = k(id, TipKomande.SLEDECI);
        assertEquals(s2, st.trenutniSlajd().id());
        assertEquals(new CrtezTrenutni(s1, PozadinaCrteza.BELA, 0), st.crtez());

        // upis bele strane podiže verziju u snimku
        crtanje.upisi(id, s1, PozadinaCrteza.BELA, telo(OLOVKA));
        assertEquals(new CrtezTrenutni(s1, PozadinaCrteza.BELA, 1), stanjeService.nastavnicko(id).crtez());

        // isključen beli ekran: strana SLAJD trenutnog slajda
        st = k(id, TipKomande.EKRAN_BEO);
        assertEquals(Ekran.NORMALAN, st.ekran());
        assertEquals(new CrtezTrenutni(s2, PozadinaCrteza.SLAJD, 0), st.crtez());
        crtanje.upisi(id, s2, PozadinaCrteza.SLAJD, telo(OLOVKA));
        assertEquals(1, stanjeService.nastavnicko(id).crtez().verzija());
        // crni ekran zadržava stranu slajda
        st = k(id, TipKomande.EKRAN_CRN);
        assertEquals(new CrtezTrenutni(s2, PozadinaCrteza.SLAJD, 1), st.crtez());
        k(id, TipKomande.EKRAN_CRN);

        // ponovo beli ekran na drugom slajdu: nova bela strana tog slajda
        st = idiNa(id, 2);
        assertEquals(new CrtezTrenutni(s3, PozadinaCrteza.SLAJD, 0), st.crtez());
        st = k(id, TipKomande.EKRAN_BEO);
        assertEquals(new CrtezTrenutni(s3, PozadinaCrteza.BELA, 0), st.crtez());
        assertEquals(s3, jdbc.queryForObject("select bela_slajd_id from izvodjenja where id = ?", Long.class, id));

        // kraj prezentacije bez belog ekrana: nema strane
        k(id, TipKomande.EKRAN_BEO);
        st = k(id, TipKomande.SLEDECI);
        assertEquals(Prikaz.KRAJ, st.prikaz());
        assertNull(st.crtez());

        // pregled: redom slajdova, SLAJD pre BELA; obrisan slajd ide na kraj bez broja
        crtanje.upisi(id, s1, PozadinaCrteza.SLAJD, telo(OLOVKA));
        crtanje.upisi(id, s3, PozadinaCrteza.SLAJD, telo(OLOVKA));
        List<CrtezIzvodjenjaInfo> sve = crtanje.sve(id);
        assertEquals(List.of(1, 1, 2, 3), sve.stream().map(CrtezIzvodjenjaInfo::slajdBroj).toList());
        assertEquals(List.of(PozadinaCrteza.SLAJD, PozadinaCrteza.BELA, PozadinaCrteza.SLAJD, PozadinaCrteza.SLAJD),
                sve.stream().map(CrtezIzvodjenjaInfo::pozadina).toList());
        prezentacijaService.obrisiSlajd(s1);
        sve = crtanje.sve(id);
        assertEquals(java.util.Arrays.asList(1, 2, null, null), sve.stream().map(CrtezIzvodjenjaInfo::slajdBroj).toList());
        assertNull(sve.get(3).slajdId());
        assertEquals(4, broj("select count(*) from crtezi_izvodjenja where izvodjenje_id = ?", id));
    }

    @Test
    void ranijiCrteziIstogSlajda() {
        PrezentacijaDetails p = prezentacija(2);
        Long s1 = p.slajdovi().get(0).id();
        Long s2 = p.slajdovi().get(1).id();

        List<Long> stara = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Long id = pokreni(p, i % 2 == 0);
            crtanje.upisi(id, s1, PozadinaCrteza.SLAJD, telo(linija("#00000" + i)));
            if (i == 0) crtanje.upisi(id, s1, PozadinaCrteza.BELA, telo(OLOVKA));
            k(id, TipKomande.ZAVRSI);
            stara.add(id);
        }
        // izvođenje sa praznom stranom (Obriši sve) i izvođenje samo na drugom slajdu se ne nude
        Long prazno = pokreni(p, false);
        crtanje.upisi(prazno, s1, PozadinaCrteza.SLAJD, telo(OLOVKA));
        crtanje.upisi(prazno, s1, PozadinaCrteza.SLAJD, telo());
        crtanje.upisi(prazno, s2, PozadinaCrteza.SLAJD, telo(OLOVKA));
        k(prazno, TipKomande.ZAVRSI);

        Long id = pokreni(p, false);
        crtanje.upisi(id, s1, PozadinaCrteza.SLAJD, telo(OLOVKA));

        List<RanijiCrtez> r = crtanje.ranije(id, s1);
        List<RanijiCrtez> slajd = r.stream().filter(x -> x.pozadina() == PozadinaCrteza.SLAJD).toList();
        List<RanijiCrtez> bela = r.stream().filter(x -> x.pozadina() == PozadinaCrteza.BELA).toList();
        assertEquals(List.of(stara.get(5), stara.get(4), stara.get(3), stara.get(2), stara.get(1)),
                slajd.stream().map(RanijiCrtez::izvodjenjeId).toList(), "najnovija prva, najviše 5, bez tekućeg i praznih");
        assertEquals("#000005", slajd.get(0).elementi().get(0).boja());
        assertEquals(List.of(stara.get(0)), bela.stream().map(RanijiCrtez::izvodjenjeId).toList());
        assertEquals(grupa.getNaziv(), bela.get(0).grupa());
        assertNull(slajd.get(0).grupa(), "izvođenje bez čuvanja nema grupu");
        assertNotNull(slajd.get(0).pocetak());
        assertEquals(List.of(prazno), crtanje.ranije(id, s2).stream().map(RanijiCrtez::izvodjenjeId).toList());
    }

    @Test
    void tablaPredavanja() {
        Predavanje pr = new Predavanje();
        pr.setPredmet(predmet);
        pr.setGrupa(grupa);
        pr.setRb(1);
        pr.setDatum(LocalDate.of(2026, 11, 23));
        pr = predavanjeRepository.save(pr);
        Long pid = pr.getId();
        try {
            assertEquals(new StranaCrteza(List.of(), 0), crtanje.tabla(pid, null).orElseThrow());
            assertTrue(crtanje.tabla(pid, 0).isEmpty(), "ništa novo posle verzije 0");
            assertEquals(1, crtanje.upisiTablu(pid, telo(OLOVKA)).verzija());
            assertEquals(2, crtanje.upisiTablu(pid, telo(OLOVKA, linija("#ffffff"))).verzija());
            StranaCrteza t = crtanje.tabla(pid, 1).orElseThrow();
            assertEquals(2, t.verzija());
            assertEquals("#FFFFFF", t.elementi().get(1).boja());
            assertTrue(crtanje.tabla(pid, 2).isEmpty());
            assertEquals(400, status(() -> crtanje.upisiTablu(pid, telo(linija("#fff")))));
            assertEquals(404, status(() -> crtanje.tabla(Long.MAX_VALUE, null)));
            assertEquals(404, status(() -> crtanje.upisiTablu(Long.MAX_VALUE, telo(OLOVKA))));
        } finally {
            predavanjeRepository.deleteById(pid);
        }
        assertEquals(0, broj("select count(*) from table_predavanja where predavanje_id = ?", pid));
    }
}
