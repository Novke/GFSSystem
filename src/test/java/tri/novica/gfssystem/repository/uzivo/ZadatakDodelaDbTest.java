package tri.novica.gfssystem.repository.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.LatexRender;
import tri.novica.gfssystem.service.banka.VarijantaService;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.service.uzivo.UcesnikService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Šema V13 nad pravom MySQL bazom (Flyway, kao CI): dodela varijante učesniku u rundi ({@code zadatak_dodele},
 * {@code UNIQUE(runda_id, ucesnik_id)}, RESTRICT prema varijanti, CASCADE sa runde i učesnika), "korišćeno na času" u
 * {@link VarijantaService} i {@link GrupaZadatakaService} iz dodela, SET NULL kad nestane grupa slajda ili student
 * učesnika, i nova polja {@code Slajd}, {@code Izvodjenje}, {@code Ucesnik}, {@code Odgovor} kroz repozitorijume. Bez
 * {@code @Transactional}: svaki poziv se zaista commit-uje, pa test čisti za sobom (prezentacija kroz servis, banka i
 * mediji kroz JDBC i {@link MedijService}).
 */
@SpringBootTest
class ZadatakDodelaDbTest {

    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>".getBytes(StandardCharsets.UTF_8);
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));
    static final String KORISCENO = "Korišćeno na času; arhiviraj.";

    @Autowired ZadatakDodelaRepository dodelaRepository;
    @Autowired SlajdRepository slajdRepository;
    @Autowired IzvodjenjeRepository izvodjenjeRepository;
    @Autowired UcesnikRepository ucesnikRepository;
    @Autowired OdgovorRepository odgovorRepository;
    @Autowired PitanjeRundaRepository rundaRepository;
    @Autowired GrupaZadatakaRepository grupaRepository;
    @Autowired VarijantaRepository varijantaRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired PredmetRepository predmetRepository;
    @Autowired GrupaZadatakaService grupaService;
    @Autowired VarijantaService varijantaService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired UcesnikService ucesnikService;
    @Autowired MedijService medijService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @MockitoBean LatexRender latexRender;

    Predmet predmet;
    GrupaDetails grupa;
    VarijantaDetails varijanta;
    Long prezentacijaId;
    Long slajdId;
    Long izvodjenjeId;
    Long ucesnikId;
    Long rundaId;
    final List<String> mediji = new ArrayList<>();
    final List<Long> studenti = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(new RenderOdgovor(true, Base64.getEncoder().encodeToString(SVG), null, null, null, 7L, false));
        predmet = new Predmet();
        predmet.setNaziv("Zadatak dodele DB " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
        grupa = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "2 → 10", null, 2, null, List.of(BROJ_A)));
        varijanta = varijanta(grupa.id());

        PrezentacijaDetails p = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Zadaci uživo", null, null));
        prezentacijaId = p.id();
        slajdId = prezentacijaService.dodajSlajd(prezentacijaId,
                new SlajdCmd(TipSlajda.INFO, "Uvod", null, null, null, false, null, null, null), null).id();
        IzvodjenjeInfo info = izvodjenjeService.pokreni(prezentacijaId, new PokreniCmd(false, null, null));
        izvodjenjeId = info.id();
        ucesnikId = ucesnikService.prijavi(info.kod(), "Ana").info().ucesnikId();
        rundaId = tx.execute(s -> {
            PitanjeRunda r = new PitanjeRunda();
            r.setIzvodjenje(izvodjenjeRepository.getReferenceById(izvodjenjeId));
            r.setSlajdId(slajdId);
            r.setRedniBroj(1);
            r.setSnimak("{}");
            r.setOtvoreno(LocalDateTime.now());
            return rundaRepository.save(r).getId();
        });
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", prezentacijaId);
        try {
            prezentacijaService.obrisi(prezentacijaId);   // izvođenja, runde, učesnici i dodele kaskadom
        } catch (SystemException e) {
            // već obrisana
        }
        Long id = predmet.getId();
        jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)", id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ? and roditelj_id is not null", id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        mediji.forEach(medijService::obrisi);
        studenti.forEach(s -> jdbc.update("delete from studenti where id = ?", s));
        predmetRepository.deleteById(id);
    }

    /** Odobrena (AKTIVNA, {@code ikadOdobrena}) varijanta grupe; njeni SVG mediji se brišu u {@link #tearDown}. */
    VarijantaDetails varijanta(Long grupaId) {
        VarijantaDetails d = varijantaService.kreiraj(grupaId, new VarijantaCmd("V1", "Pretvori 1011.", "$11$", ODGOVOR_A, null, null));
        mediji.add(d.svgId());
        mediji.add(d.resenjeSvgId());
        return varijantaService.odobri(d.id());
    }

    Long dodeli(Long varijantaId) {
        return tx.execute(s -> {
            ZadatakDodela d = new ZadatakDodela();
            d.setRunda(rundaRepository.getReferenceById(rundaId));
            d.setUcesnik(ucesnikRepository.getReferenceById(ucesnikId));
            d.setVarijanta(varijantaRepository.getReferenceById(varijantaId));
            d.setKreirano(LocalDateTime.now());
            return dodelaRepository.save(d).getId();
        });
    }

    long brojDodela() {
        return jdbc.queryForObject("select count(*) from zadatak_dodele where runda_id = ?", Long.class, rundaId);
    }

    static void ocekuj409(String poruka, Runnable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv::run);
        assertEquals(409, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    @Test
    void jednaDodelaPoRundiIUcesnikuIUpitiRepozitorijuma() {
        assertFalse(dodelaRepository.existsByRundaIdAndUcesnikId(rundaId, ucesnikId));
        Long dodelaId = dodeli(varijanta.id());

        assertThrows(DataIntegrityViolationException.class, () -> dodeli(varijanta.id()));
        assertEquals(1, brojDodela());
        assertTrue(dodelaRepository.existsByRundaIdAndUcesnikId(rundaId, ucesnikId));
        assertEquals(dodelaId, dodelaRepository.findByRundaIdAndUcesnikId(rundaId, ucesnikId).orElseThrow().getId());
        assertTrue(dodelaRepository.findByRundaIdAndUcesnikId(rundaId, ucesnikId + 1).isEmpty());
        assertTrue(dodelaRepository.existsByVarijantaId(varijanta.id()));
        assertTrue(dodelaRepository.existsByVarijantaGrupaId(grupa.id()));
        assertFalse(dodelaRepository.existsByVarijantaId(varijanta.id() + 1000));

        List<Object[]> parovi = dodelaRepository.parovi(rundaId);
        assertEquals(1, parovi.size());
        assertArrayEquals(new Object[]{ucesnikId, varijanta.id()}, parovi.get(0));
        List<Object[]> paroviIzvodjenja = dodelaRepository.paroviIzvodjenja(izvodjenjeId);
        assertEquals(1, paroviIzvodjenja.size());
        assertArrayEquals(new Object[]{rundaId, ucesnikId, varijanta.id()}, paroviIzvodjenja.get(0));
    }

    @Test
    void koriscenaVarijantaIGrupaSeNeBrisuDokPostojiDodela() {
        dodeli(varijanta.id());
        assertTrue(varijantaService.koriscenoNaCasu(varijanta.id()));
        assertTrue(grupaService.koriscenoNaCasu(grupa.id()));

        ocekuj409(KORISCENO, () -> varijantaService.obrisi(varijanta.id()));
        assertTrue(varijantaRepository.existsById(varijanta.id()));
        ocekuj409(KORISCENO, () -> grupaService.obrisi(grupa.id()));
        assertTrue(grupaRepository.existsById(grupa.id()));
        assertTrue(varijantaRepository.existsById(varijanta.id()));

        // brisanje završenog izvođenja briše runde i učesnike, a sa njima i dodele (CASCADE): varijanta se tada briše
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null, kraj = now(6) where id = ?", izvodjenjeId);
        izvodjenjeService.obrisi(izvodjenjeId);
        assertEquals(0, brojDodela());
        assertFalse(varijantaService.koriscenoNaCasu(varijanta.id()));
        assertFalse(grupaService.koriscenoNaCasu(grupa.id()));

        varijantaService.obrisi(varijanta.id());
        assertFalse(varijantaRepository.existsById(varijanta.id()));
        // odobrena varijanta je mogla ući u snimak runde: njeni SVG mediji ostaju
        assertEquals(2, jdbc.queryForObject("select count(*) from mediji where id in (?, ?)", Integer.class,
                varijanta.svgId(), varijanta.resenjeSvgId()));
        grupaService.obrisi(grupa.id());
        assertFalse(grupaRepository.existsById(grupa.id()));
    }

    @Test
    void varijantaSeNeBriseNiMimoServisa() {
        dodeli(varijanta.id());
        // FK RESTRICT je poslednja brana i kad servis zaobiđe proveru (npr. stara slika)
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from varijante where id = ?", varijanta.id()));
        assertEquals(1, brojDodela());
    }

    @Test
    void brisanjeGrupeIStudentaOstavljaSlajdIUcesnika() {
        GrupaDetails prazna = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "10 → 2", null, null, null, null));
        tx.executeWithoutResult(s -> {
            Slajd slajd = slajdRepository.findById(slajdId).orElseThrow();
            slajd.setGrupaZadataka(grupaRepository.getReferenceById(prazna.id()));
        });
        assertEquals(prazna.id(), jdbc.queryForObject("select grupa_zadataka_id from slajdovi where id = ?", Long.class, slajdId));

        grupaService.obrisi(prazna.id());
        assertTrue(slajdRepository.existsById(slajdId));
        assertNull(jdbc.queryForObject("select grupa_zadataka_id from slajdovi where id = ?", Long.class, slajdId));

        jdbc.update("insert into studenti (ime, prezime, godina, indeks) values ('Zadatak', 'Proba', 2025, ?)",
                "ZD-" + UUID.randomUUID().toString().substring(0, 8));
        Long studentId = jdbc.queryForObject("select max(id) from studenti where ime = 'Zadatak'", Long.class);
        studenti.add(studentId);
        tx.executeWithoutResult(s -> ucesnikRepository.findById(ucesnikId).orElseThrow()
                .setStudent(studentRepository.getReferenceById(studentId)));
        assertEquals(studentId, jdbc.queryForObject("select student_id from ucesnici where id = ?", Long.class, ucesnikId));

        jdbc.update("delete from studenti where id = ?", studentId);
        assertTrue(ucesnikRepository.existsById(ucesnikId));
        assertNull(jdbc.queryForObject("select student_id from ucesnici where id = ?", Long.class, ucesnikId));
    }

    @Test
    void novaPoljaKrozRepozitorijume() {
        jdbc.update("insert into studenti (ime, prezime, godina, indeks) values ('Zadatak', 'Polja', 2025, ?)",
                "ZP-" + UUID.randomUUID().toString().substring(0, 8));
        Long studentId = jdbc.queryForObject("select max(id) from studenti where ime = 'Zadatak'", Long.class);
        studenti.add(studentId);
        String polja = "{\"a\":{\"vrednost\":\"11\",\"tacno\":true}}";

        Long odgovorId = tx.execute(s -> {
            Slajd slajd = slajdRepository.findById(slajdId).orElseThrow();
            slajd.setGrupaZadataka(grupaRepository.getReferenceById(grupa.id()));
            slajd.setZadatakVremeSekunde(120);
            slajd.setZadatakBrojVarijanti(4);
            Izvodjenje iz = izvodjenjeRepository.findById(izvodjenjeId).orElseThrow();
            iz.setBoduje(true);
            iz.setResenjePrikazano(true);
            iz.setZadatakBrojVarijanti(0);
            Ucesnik u = ucesnikRepository.findById(ucesnikId).orElseThrow();
            u.setStudent(studentRepository.getReferenceById(studentId));
            Odgovor o = new Odgovor();
            o.setRunda(rundaRepository.getReferenceById(rundaId));
            o.setUcesnik(u);
            o.setPolja(polja);
            o.setVarijantaId(varijanta.id());
            o.setTacno(true);
            o.setVremeMs(1500);
            o.setKreirano(LocalDateTime.now());
            return odgovorRepository.save(o).getId();
        });

        tx.executeWithoutResult(s -> {
            Slajd slajd = slajdRepository.findById(slajdId).orElseThrow();
            assertEquals(grupa.id(), slajd.getGrupaZadataka().getId());
            assertEquals("2 → 10", slajd.getGrupaZadataka().getNaziv());
            assertEquals(120, slajd.getZadatakVremeSekunde());
            assertEquals(4, slajd.getZadatakBrojVarijanti());
            Izvodjenje iz = izvodjenjeRepository.findById(izvodjenjeId).orElseThrow();
            assertTrue(iz.isBoduje());
            assertTrue(iz.isResenjePrikazano());
            assertEquals(0, iz.getZadatakBrojVarijanti());
            assertEquals(studentId, ucesnikRepository.findById(ucesnikId).orElseThrow().getStudent().getId());
            Odgovor o = odgovorRepository.findById(odgovorId).orElseThrow();
            assertEquals(polja, o.getPolja());
            assertEquals(varijanta.id(), o.getVarijantaId());
        });

        // izvođenje i učesnik bez novih vrednosti: podrazumevano false i null
        tx.executeWithoutResult(s -> {
            Izvodjenje iz = izvodjenjeRepository.findById(izvodjenjeId).orElseThrow();
            iz.setBoduje(false);
            iz.setResenjePrikazano(false);
            iz.setZadatakBrojVarijanti(null);
            ucesnikRepository.findById(ucesnikId).orElseThrow().setStudent(null);
            Slajd slajd = slajdRepository.findById(slajdId).orElseThrow();
            slajd.setGrupaZadataka(null);
            slajd.setZadatakVremeSekunde(null);
            slajd.setZadatakBrojVarijanti(null);
        });
        Map<String, Object> izv = jdbc.queryForMap("select cast(boduje as unsigned) boduje, "
                + "cast(resenje_prikazano as unsigned) resenje_prikazano, zadatak_broj_varijanti from izvodjenja where id = ?",
                izvodjenjeId);
        assertEquals(0L, ((Number) izv.get("boduje")).longValue());
        assertEquals(0L, ((Number) izv.get("resenje_prikazano")).longValue());
        assertNull(izv.get("zadatak_broj_varijanti"));
        assertNull(jdbc.queryForObject("select student_id from ucesnici where id = ?", Long.class, ucesnikId));
        assertNull(jdbc.queryForObject("select grupa_zadataka_id from slajdovi where id = ?", Long.class, slajdId));
    }
}
