package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static tri.novica.gfssystem.utility.Promena.na;

/**
 * MCP izmene grupe {@link GrupaZadatakaService} nad pravom MySQL bazom (Flyway V1..V12, kao CI): delimična izmena spaja
 * patch nad zaključanim stanjem (izostavljeno ostaje, {@code null} briše), a šema polja se pod ključevima prvo isproba nad
 * varijantama i odbija sa 409 i spiskom pogođenih kad bi neka izgubila odgovor ili AKTIVNA pala u NACRT. Bez
 * {@code @Transactional}: svaki poziv servisa se zaista commit-uje, pa test čisti za sobom kroz JDBC.
 */
@SpringBootTest
class GrupaZadatakaMcpDbTest {

    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Polje TEKST_B = new Polje("b", "b) heks u binarni", TipPolja.TEKST, null, null, null, null, null, null);
    static final Polje IZBOR_C = izbor(new PoljeOpcija(1L, "Mur"), new PoljeOpcija(2L, "Mili"), new PoljeOpcija(3L, "Tjuring"));
    static final String ODGOVOR_A = "{\"a\":{\"broj\":11.0}}";

    @Autowired GrupaZadatakaService service;
    @Autowired GrupaZadatakaRepository grupaRepository;
    @Autowired VarijantaRepository varijantaRepository;
    @Autowired PredmetRepository predmetRepository;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    Predmet predmet;

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Banka MCP grupe DB test " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
    }

    @AfterEach
    void tearDown() {
        Long id = predmet.getId();
        String grupePredmeta = "(select id from grupe_zadataka where predmet_id = ?)";
        jdbc.update("delete from varijante where grupa_id in " + grupePredmeta, id);
        jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        predmetRepository.deleteById(id);
    }

    static Polje izbor(PoljeOpcija... opcije) {
        return new Polje("c", "Koji automat?", TipPolja.JEDAN_TACAN, null, null, null, null, null, List.of(opcije));
    }

    GrupaDetails grupa(String naziv, Integer tezina, List<Polje> polja) {
        return service.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, naziv, "Opis grupe", tezina,
                "Uputstvo: brojevi do 255.", polja));
    }

    /** Varijanta upisana mimo servisa, sa zadatim {@code rb} (redosled zaključavanja nije redosled id-jeva). */
    Long varijanta(Long grupaId, int rb, StatusVarijante status, String odgovori) {
        Varijanta v = new Varijanta();
        v.setGrupa(grupaRepository.findById(grupaId).orElseThrow());
        v.setRb(rb);
        v.setTekst("Pretvori $1011_2$ u dekadni.");
        v.setOdgovori(odgovori);
        v.setStatus(status);
        v.setIzvor(IzvorVarijante.CLAUDE);
        v.setRenderStatus(RenderStatus.OK);
        v.setKreirano(LocalDateTime.now(clock));
        v.setIzmenjeno(LocalDateTime.now(clock));
        return varijantaRepository.save(v).getId();
    }

    Map<String, Object> grupaRed(Long id) {
        return jdbc.queryForMap("select naziv, opis, tezina, uputstvo, polja, izmenjeno from grupe_zadataka where id = ?", id);
    }

    Map<String, Object> varijantaRed(Long id) {
        return jdbc.queryForMap("select status, odgovori, izmenjeno from varijante where id = ?", id);
    }

    static void ocekuj(int kod, String poruka, Runnable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv::run);
        assertEquals(kod, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    // ---------------------------------------------------------------- izmeniDelimicno

    @Test
    void delimicnaIzmenaMenjaSamoPoslataPoljaANullBrise() {
        GrupaDetails g = grupa("2 → 10", 3, List.of(BROJ_A));
        String polja = (String) grupaRed(g.id()).get("polja");

        GrupaDetails d = service.izmeniDelimicno(g.id(), new GrupaPatch(na("  2 u 10 "), null, null, null));

        assertEquals("2 u 10", d.naziv());
        assertEquals("Opis grupe", d.opis());
        assertEquals(3, d.tezina());
        assertEquals("Uputstvo: brojevi do 255.", d.uputstvo());
        assertEquals(List.of(BROJ_A.kljuc()), d.polja().stream().map(Polje::kljuc).toList());
        Map<String, Object> red = grupaRed(g.id());
        assertEquals("2 u 10", red.get("naziv"));
        assertEquals("Uputstvo: brojevi do 255.", red.get("uputstvo"));
        assertEquals(3, ((Number) red.get("tezina")).intValue());
        assertEquals(polja, red.get("polja"));

        GrupaDetails bezOpisa = service.izmeniDelimicno(g.id(), new GrupaPatch(null, na(null), null, null));

        assertNull(bezOpisa.opis());
        assertNull(grupaRed(g.id()).get("opis"));
        assertEquals("2 u 10", bezOpisa.naziv());
        assertEquals(3, bezOpisa.tezina());
        assertEquals("Uputstvo: brojevi do 255.", bezOpisa.uputstvo());

        GrupaDetails bezUputstva = service.izmeniDelimicno(g.id(), new GrupaPatch(null, null, na(5), na(null)));
        assertEquals(5, bezUputstva.tezina());
        assertNull(bezUputstva.uputstvo());
        assertEquals("2 u 10", bezUputstva.naziv());
    }

    @Test
    void delimicnaIzmenaImaIstaPravilaKaoIzmena() {
        GrupaDetails g = grupa("2 → 10", 3, List.of(BROJ_A));
        grupa("16 → 2", null, List.of());
        varijanta(g.id(), 1, StatusVarijante.NACRT, ODGOVOR_A);
        Map<String, Object> pre = grupaRed(g.id());

        ocekuj(400, "Grupa sa tim nazivom već postoji.",
                () -> service.izmeniDelimicno(g.id(), new GrupaPatch(na("16 → 2"), null, null, null)));
        ocekuj(400, "Težina je obavezna za grupu sa varijantama.",
                () -> service.izmeniDelimicno(g.id(), new GrupaPatch(null, null, na(null), null)));
        ocekuj(400, "Neispravan format podataka.", () -> service.izmeniDelimicno(g.id(), null));
        ocekuj(404, "Grupa nije pronađena.", () -> service.izmeniDelimicno(-1L, new GrupaPatch(na("X"), null, null, null)));
        assertEquals(pre, grupaRed(g.id()));
    }

    /**
     * Fasada pre izmene čita grupu ({@code detalji}) u istom zahtevu, a uz open-in-view taj EntityManager traje ceo zahtev:
     * izmena pod ključem ipak spaja patch nad stanjem iz baze (Novicina izmena posle čitanja ostaje) i upisuje se.
     */
    @Test
    void delimicnaIzmenaPosleCitanjaUIstomZahtevuSpajaNadStanjemIzBaze() {
        GrupaDetails g = grupa("2 → 10", 3, List.of(BROJ_A));

        GrupaDetails d = uIstomZahtevu(() -> {
            assertEquals("Uputstvo: brojevi do 255.", service.detalji(g.id()).uputstvo());
            jdbc.update("update grupe_zadataka set uputstvo = 'Novicino uputstvo' where id = ?", g.id());
            return service.izmeniDelimicno(g.id(), new GrupaPatch(na("2 u 10"), null, null, null));
        });

        assertEquals("2 u 10", d.naziv());
        assertEquals("Novicino uputstvo", d.uputstvo());
        Map<String, Object> red = grupaRed(g.id());
        assertEquals("2 u 10", red.get("naziv"));
        assertEquals("Novicino uputstvo", red.get("uputstvo"));
    }

    // ---------------------------------------------------------------- izmeniPolja

    @Test
    void dodatoPoljeDokPostojiAktivnaJe409SaNjenimIdomBezUpisa() {
        GrupaDetails g = grupa("2 → 10", 2, List.of(BROJ_A));
        Long aktivna = varijanta(g.id(), 1, StatusVarijante.AKTIVNA, ODGOVOR_A);
        Long nacrt = varijanta(g.id(), 2, StatusVarijante.NACRT, ODGOVOR_A);
        Map<String, Object> grupaPre = grupaRed(g.id());
        Map<String, Object> aktivnaPre = varijantaRed(aktivna);
        Map<String, Object> nacrtPre = varijantaRed(nacrt);

        ocekuj(409, "Izmena polja bi pogodila varijante: " + aktivna + ".",
                () -> service.izmeniPolja(g.id(), List.of(BROJ_A, TEKST_B)));

        assertEquals(grupaPre, grupaRed(g.id()));
        assertEquals(aktivnaPre, varijantaRed(aktivna));
        assertEquals(nacrtPre, varijantaRed(nacrt));
    }

    @Test
    void uklonjenaOpcijaNaKojuPokazujeOdgovorJe409SaIdjevimaRastuce() {
        GrupaDetails g = grupa("Automati", 3, List.of(IZBOR_C));
        // rb obrnut od id-ja: ključevi idu po rb, poruka po id-ju; i arhivirana bi izgubila odgovor
        Long prvi = varijanta(g.id(), 3, StatusVarijante.NACRT, "{\"c\":{\"tacna\":2}}");
        Long nepogodjena = varijanta(g.id(), 2, StatusVarijante.NACRT, "{\"c\":{\"tacna\":1}}");
        Long arhivirana = varijanta(g.id(), 1, StatusVarijante.ARHIVIRANA, "{\"c\":{\"tacna\":2}}");
        Map<String, Object> grupaPre = grupaRed(g.id());

        ocekuj(409, "Izmena polja bi pogodila varijante: " + prvi + ", " + arhivirana + ".",
                () -> service.izmeniPolja(g.id(), List.of(izbor(new PoljeOpcija(1L, "Mur"), new PoljeOpcija(3L, "Tjuring")))));

        assertEquals(grupaPre, grupaRed(g.id()));
        assertEquals("{\"c\":{\"tacna\":2}}", varijantaRed(prvi).get("odgovori"));
        assertEquals("{\"c\":{\"tacna\":1}}", varijantaRed(nepogodjena).get("odgovori"));
        assertEquals("{\"c\":{\"tacna\":2}}", varijantaRed(arhivirana).get("odgovori"));
    }

    @Test
    void preimenovanaINovaOpcijaProlazeIOdgovoriOstaju() {
        GrupaDetails g = grupa("Automati", 3, List.of(IZBOR_C));
        Long aktivna = varijanta(g.id(), 1, StatusVarijante.AKTIVNA, "{\"c\":{\"tacna\":2}}");

        GrupaDetails d = service.izmeniPolja(g.id(), List.of(izbor(new PoljeOpcija(1L, "Mur"),
                new PoljeOpcija(2L, "Milijev automat"), new PoljeOpcija(3L, "Tjuring"), new PoljeOpcija(null, "Potisni"))));

        List<PoljeOpcija> opcije = d.polja().get(0).opcije();
        assertEquals(List.of(1L, 2L, 3L, 4L), opcije.stream().map(PoljeOpcija::id).toList());
        assertEquals("Milijev automat", opcije.get(1).tekst());
        assertEquals("Automati", d.naziv());
        assertEquals(3, d.tezina());
        assertEquals("Uputstvo: brojevi do 255.", d.uputstvo());
        assertEquals("AKTIVNA", varijantaRed(aktivna).get("status"));
        assertEquals("{\"c\":{\"tacna\":2}}", varijantaRed(aktivna).get("odgovori"));
        assertTrue(((String) grupaRed(g.id()).get("polja")).contains("Milijev automat"));
    }

    @Test
    void poljaBezPogodjenihSeUpisujuKaoIzmena() {
        GrupaDetails g = grupa("2 → 10", 2, List.of(BROJ_A, TEKST_B));
        // nacrt bez vrednosti za b: uklanjanje b mu ne oduzima odgovor, dodavanje polja nacrt ne spušta
        Long nacrt = varijanta(g.id(), 1, StatusVarijante.NACRT, ODGOVOR_A);

        GrupaDetails bezB = service.izmeniPolja(g.id(), List.of(BROJ_A));
        assertEquals(List.of("a"), bezB.polja().stream().map(Polje::kljuc).toList());
        assertEquals(ODGOVOR_A, varijantaRed(nacrt).get("odgovori"));

        Polje d = new Polje("d", "d) oktalni", TipPolja.BROJ, null, null, null, null, null, null);
        GrupaDetails saD = service.izmeniPolja(g.id(), List.of(BROJ_A, d));
        assertEquals(List.of("a", "d"), saD.polja().stream().map(Polje::kljuc).toList());
        assertEquals("NACRT", varijantaRed(nacrt).get("status"));
        assertEquals(ODGOVOR_A, varijantaRed(nacrt).get("odgovori"));

        // promena tipa dok postoji aktivna ostaje postojeći 409 (ništa upisano)
        Long aktivna = varijanta(g.id(), 2, StatusVarijante.AKTIVNA, "{\"a\":{\"broj\":11.0},\"d\":{\"broj\":13.0}}");
        Polje aTekst = new Polje("a", "a) binarni u dekadni", TipPolja.TEKST, null, null, null, null, null, null);
        ocekuj(409, "Tip polja 'a' se ne menja dok grupa ima aktivne varijante.",
                () -> service.izmeniPolja(g.id(), List.of(aTekst, d)));
        assertEquals("AKTIVNA", varijantaRed(aktivna).get("status"));

        ocekuj(400, "Neispravan format podataka.", () -> service.izmeniPolja(g.id(), null));
        ocekuj(404, "Grupa nije pronađena.", () -> service.izmeniPolja(-1L, List.of(BROJ_A)));
    }

    /** Šema se isproba nad stanjem iz baze i kad je fasada grupu pročitala ranije u istom zahtevu (open-in-view). */
    @Test
    void poljaPosleCitanjaUIstomZahtevuSeUpisuju() {
        GrupaDetails g = grupa("2 → 10", 2, List.of(BROJ_A));

        GrupaDetails d = uIstomZahtevu(() -> {
            service.detalji(g.id());
            return service.izmeniPolja(g.id(), List.of(BROJ_A, TEKST_B));
        });

        assertEquals(List.of("a", "b"), d.polja().stream().map(Polje::kljuc).toList());
        assertTrue(((String) grupaRed(g.id()).get("polja")).contains("\"b\""));
    }

    // ---------------------------------------------------------------- open-in-view: braća i stablo iz baze

    /**
     * Fasada je pre izmene učitala celo stablo ({@code detalji}) u istom EntityManager-u; Novica u međuvremenu preimenuje
     * brata. Provera naziva pod ključem mora videti novo ime iz baze, ne brata pročitanog pre ključa.
     */
    @Test
    void delimicnaIzmenaPosleCitanjaVidiNovoImeBrata() {
        GrupaDetails a = grupa("2 → 10", 3, List.of(BROJ_A));
        GrupaDetails b = grupa("16 → 2", null, List.of());

        uIstomZahtevu(() -> {
            service.detalji(a.id());
            jdbc.update("update grupe_zadataka set naziv = 'X' where id = ?", b.id());
            ocekuj(400, "Grupa sa tim nazivom već postoji.",
                    () -> service.izmeniDelimicno(a.id(), new GrupaPatch(na("X"), null, null, null)));
            return null;
        });

        assertEquals("2 → 10", grupaRed(a.id()).get("naziv"));
    }

    /** Premeštanje posle čitanja u istom zahtevu proverava naziv nad decom cilja iz baze. */
    @Test
    void premestanjePosleCitanjaVidiNovoImeDeteta() {
        GrupaDetails a = grupa("X", null, List.of());
        GrupaDetails nedelja = grupa("Nedelja 3", null, List.of());
        GrupaDetails uvod = service.kreiraj(new GrupaCmd(null, nedelja.id(), null, "Uvod", null, null, null, List.of()));

        uIstomZahtevu(() -> {
            service.detalji(a.id());
            jdbc.update("update grupe_zadataka set naziv = 'x' where id = ?", uvod.id());
            ocekuj(400, "Grupa sa tim nazivom već postoji.",
                    () -> service.premesti(a.id(), new PremestiCmd(nedelja.id(), null)));
            return null;
        });

        assertNull(jdbc.queryForObject("select roditelj_id from grupe_zadataka where id = ?", Long.class, a.id()));
    }

    /**
     * Brisanje prazne posle čitanja u istom zahtevu prenumeriše braću nad {@code rb} iz baze: C je pročitan sa {@code rb}
     * 3 i posle brisanja opet dobija 3, pa bi se zastareo entitet smatrao nepromenjenim i u bazi bi ostao Novicin 4.
     */
    @Test
    void brisanjePrazneUIstomZahtevuPrenumeriseBracuIzBaze() {
        grupa("A", null, List.of());
        GrupaDetails b = grupa("B", null, List.of());
        GrupaDetails c = grupa("C", null, List.of());
        GrupaDetails d = grupa("D", null, List.of());

        uIstomZahtevu(() -> {
            service.detalji(b.id());
            // Novica je u međuvremenu zamenio mesta C i D
            jdbc.update("update grupe_zadataka set rb = case id when ? then 4 when ? then 3 end where id in (?, ?)",
                    c.id(), d.id(), c.id(), d.id());
            service.obrisiPraznu(b.id());
            return null;
        });

        assertEquals(List.of("A:1", "D:2", "C:3"), jdbc.queryForList("select concat(naziv, ':', rb) from grupe_zadataka "
                + "where predmet_id = ? order by rb, id", String.class, predmet.getId()));
    }

    <T> T uIstomZahtevu(Supplier<T> posao) {
        return IstiZahtev.uIstomZahtevu(emf, posao);
    }
}
