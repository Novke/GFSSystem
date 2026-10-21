package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.utility.Serija;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link IskljuceneTemeService} i oznaka isključenosti u {@link GrupaZadatakaService} nad pravom MySQL bazom (Flyway
 * V1..V12, kao CI): tema nad grupom isključuje celo podstablo (sopstvena tema deteta ima prednost), tema bez grupe je samo
 * tekst, poruke iz ugovora (dupla grupa, isti normalizovan opis bez grupe, grupa drugog predmeta, dužina opisa), brisanje
 * grupe ostavlja temu bez grupe i ponovljen opis bez grupe pod ključem predmeta. Bez {@code @Transactional}: svaki poziv
 * servisa se zaista commit-uje, pa test čisti za sobom kroz JDBC (teme nestaju sa predmetom, ON DELETE CASCADE).
 */
@SpringBootTest
class IskljuceneTemeDbTest {

    static final String GRUPA_ISKLJUCENA = "409 Ova grupa je već isključena.";
    static final String TEMA_ISKLJUCENA = "409 Ova tema je već isključena.";
    static final String NEISPRAVAN_OPIS = "400 Opis teme mora imati od 1 do 300 znakova.";

    @Autowired IskljuceneTemeService service;
    @Autowired GrupaZadatakaService grupaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transakcije;

    Predmet predmet;
    Predmet drugiPredmet;

    @BeforeEach
    void setUp() {
        predmet = noviPredmet("Isključene teme DB test ");
        drugiPredmet = noviPredmet("Isključene teme DB test (drugi) ");
    }

    @AfterEach
    void tearDown() {
        for (Predmet p : List.of(predmet, drugiPredmet)) {
            jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", p.getId());
            jdbc.update("delete from grupe_zadataka where predmet_id = ?", p.getId());
            predmetRepository.deleteById(p.getId());
            assertEquals(0, jdbc.queryForObject("select count(*) from iskljucene_teme where predmet_id = ?",
                    Integer.class, p.getId()));
        }
    }

    Predmet noviPredmet(String naziv) {
        Predmet p = new Predmet();
        p.setNaziv(naziv + UUID.randomUUID().toString().substring(0, 8));
        return predmetRepository.save(p);
    }

    GrupaDetails koren(Predmet p, String naziv) {
        return grupaService.kreiraj(new GrupaCmd(p.getId(), null, Namena.PREDAVANJE, naziv, null, null, null, List.of()));
    }

    GrupaDetails podgrupa(GrupaDetails roditelj, String naziv) {
        return grupaService.kreiraj(new GrupaCmd(null, roditelj.id(), null, naziv, null, null, null, List.of()));
    }

    IskljucenaTemaInfo iskljuci(String opis, Long grupaId) {
        return service.dodaj(predmet.getId(), new IskljucenaTemaCmd(opis, grupaId), IzvorVarijante.RUCNO, null);
    }

    int brojTema() {
        return jdbc.queryForObject("select count(*) from iskljucene_teme where predmet_id = ?", Integer.class, predmet.getId());
    }

    /** Čvorovi stabla po nazivu (nazivi su u testu jedinstveni u celom stablu). */
    Map<String, GrupaCvor> cvorovi() {
        Map<String, GrupaCvor> rezultat = new HashMap<>();
        Deque<GrupaCvor> red = new ArrayDeque<>(grupaService.stablo(predmet.getId(), Namena.PREDAVANJE));
        while (!red.isEmpty()) {
            GrupaCvor c = red.poll();
            rezultat.put(c.naziv(), c);
            red.addAll(c.deca());
        }
        return rezultat;
    }

    /** {@code iskljucenjeId} po nazivu čvora, {@code null} za neisključene (i provera da se {@code iskljucena} slaže). */
    Map<String, Long> oznake() {
        Map<String, Long> rezultat = new HashMap<>();
        cvorovi().forEach((naziv, c) -> {
            assertEquals(c.iskljucenjeId() != null, c.iskljucena(), naziv);
            rezultat.put(naziv, c.iskljucenjeId());
        });
        return rezultat;
    }

    static Map<String, Long> mapa(Object... parovi) {
        Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < parovi.length; i += 2) {
            m.put((String) parovi[i], (Long) parovi[i + 1]);
        }
        return m;
    }

    static String ishod(Supplier<?> posao) {
        try {
            return "ok " + posao.get();
        } catch (SystemException e) {
            return e.getCode() + " " + e.getMessage();
        }
    }

    @Test
    void temaNadUnutrasnjimCvoromIskljucujePodstablo() {
        GrupaDetails r = koren(predmet, "Brojni sistemi");
        GrupaDetails a = podgrupa(r, "Razlomljeni brojevi");
        GrupaDetails a1 = podgrupa(a, "10 → 2");
        GrupaDetails a2 = podgrupa(a, "2 → 10");
        GrupaDetails b = podgrupa(r, "Celi brojevi");

        IskljucenaTemaInfo tema = iskljuci("Razlomljeni (decimalni) brojevi 10 → 2 i 2 → 10", a.id());
        assertEquals(mapa("Brojni sistemi", null, "Razlomljeni brojevi", tema.id(), "10 → 2", tema.id(),
                "2 → 10", tema.id(), "Celi brojevi", null), oznake());
        GrupaDetails dete = grupaService.detalji(a2.id());
        assertTrue(dete.iskljucena());
        assertEquals(tema.id(), dete.iskljucenjeId());
        assertTrue(grupaService.detalji(a.id()).iskljucena());
        assertFalse(grupaService.detalji(r.id()).iskljucena());
        assertNull(grupaService.detalji(r.id()).iskljucenjeId());
        assertFalse(grupaService.detalji(b.id()).iskljucena());

        // sopstvena tema deteta ima prednost pred temom pretka, u stablu i u detaljima
        IskljucenaTemaInfo sopstvena = iskljuci("Samo 10 → 2", a1.id());
        assertEquals(mapa("Brojni sistemi", null, "Razlomljeni brojevi", tema.id(), "10 → 2", sopstvena.id(),
                "2 → 10", tema.id(), "Celi brojevi", null), oznake());
        assertEquals(sopstvena.id(), grupaService.detalji(a1.id()).iskljucenjeId());
        assertEquals(tema.id(), grupaService.detalji(a2.id()).iskljucenjeId());

        // isključenje drugog predmeta (i druge namene) se ne vidi ovde
        GrupaDetails tudja = koren(drugiPredmet, "Brojni sistemi");
        service.dodaj(drugiPredmet.getId(), new IskljucenaTemaCmd("Sve", tudja.id()), IzvorVarijante.RUCNO, null);
        GrupaDetails test = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.TEST, "Kolokvijum", null, null, null, List.of()));
        iskljuci("Kolokvijum", test.id());
        assertNull(oznake().get("Brojni sistemi"));
        assertEquals(1, grupaService.stablo(predmet.getId(), Namena.TEST).size());
        assertTrue(grupaService.stablo(predmet.getId(), Namena.TEST).get(0).iskljucena());
    }

    @Test
    void temaBezGrupeNeMenjaStabloIListaJeNajnovijePrve() {
        GrupaDetails r = koren(predmet, "Brojni sistemi");
        GrupaDetails a = podgrupa(r, "Razlomljeni brojevi");
        IskljucenaTemaInfo nadGrupom = iskljuci("Razlomljeni brojevi", a.id());
        IskljucenaTemaInfo bezGrupe = service.dodaj(predmet.getId(), new IskljucenaTemaCmd("  Pokretni zarez (IEEE 754)  ", null),
                IzvorVarijante.CLAUDE, "uvoz-1");

        assertEquals(mapa("Brojni sistemi", null, "Razlomljeni brojevi", nadGrupom.id()), oznake());
        assertEquals("Pokretni zarez (IEEE 754)", bezGrupe.opis());
        assertNull(bezGrupe.grupaId());
        assertEquals(List.of(), bezGrupe.putanja());
        assertEquals(IzvorVarijante.CLAUDE, bezGrupe.izvor());
        assertEquals("uvoz-1", bezGrupe.serija());
        assertEquals(predmet.getId(), bezGrupe.predmetId());
        assertNotNull(bezGrupe.kreirano());

        List<IskljucenaTemaInfo> lista = service.lista(predmet.getId());
        assertEquals(List.of(bezGrupe.id(), nadGrupom.id()), lista.stream().map(IskljucenaTemaInfo::id).toList());
        IskljucenaTemaInfo grupna = lista.get(1);
        assertEquals(a.id(), grupna.grupaId());
        assertEquals(List.of(new GrupaPutanja(r.id(), "Brojni sistemi"), new GrupaPutanja(a.id(), "Razlomljeni brojevi")),
                grupna.putanja());
        assertEquals(IzvorVarijante.RUCNO, grupna.izvor());
        assertNull(grupna.serija());
        assertEquals(Map.of(a.id(), nadGrupom.id()), service.iskljuceneGrupe(predmet.getId()));
        assertEquals(List.of(), service.lista(drugiPredmet.getId()));
    }

    @Test
    void duplaTemaZaIstuGrupuJe409() {
        GrupaDetails a = koren(predmet, "Razlomljeni brojevi");
        iskljuci("Razlomljeni brojevi", a.id());
        assertEquals(GRUPA_ISKLJUCENA, ishod(() -> iskljuci("Drugi opis iste grupe", a.id())));
        assertEquals(1, brojTema());
    }

    @Test
    void istiNormalizovanOpisBezGrupeJe409() {
        IskljucenaTemaInfo prva = iskljuci("razlomljeni brojevi", null);
        for (String opis : List.of("Razlomljeni  brojevi", "  RAZLOMLJENI brojevi ", "razlomljeni\t\nbrojevi")) {
            assertEquals(TEMA_ISKLJUCENA, ishod(() -> iskljuci(opis, null)), opis);
        }
        assertEquals(1, brojTema());
        assertEquals(List.of(prva.id()), service.lista(predmet.getId()).stream().map(IskljucenaTemaInfo::id).toList());

        // drugi opis bez grupe prolazi, a isti opis nad grupom nije duplikat teme bez grupe (i obrnuto)
        assertTrue(ishod(() -> iskljuci("Razlomljeni brojevi u bazi 16", null)).startsWith("ok "));
        GrupaDetails a = koren(predmet, "Pokretni zarez");
        assertTrue(ishod(() -> iskljuci("Razlomljeni brojevi", a.id())).startsWith("ok "));
        assertTrue(ishod(() -> iskljuci("Pokretni zarez", null)).startsWith("ok "));
        // isti opis u drugom predmetu je druga tema
        assertTrue(ishod(() -> service.dodaj(drugiPredmet.getId(), new IskljucenaTemaCmd("razlomljeni brojevi", null),
                IzvorVarijante.RUCNO, null)).startsWith("ok "));
        assertEquals(4, brojTema());
    }

    @Test
    void grupaDrugogPredmetaJe400INepoznataGrupa404() {
        GrupaDetails tudja = koren(drugiPredmet, "Brojni sistemi");
        assertEquals("400 Grupa nije u ovom predmetu.", ishod(() -> iskljuci("Brojni sistemi", tudja.id())));
        assertEquals("404 Grupa nije pronađena.", ishod(() -> iskljuci("Brojni sistemi", Long.MAX_VALUE)));
        assertEquals(0, brojTema());
        assertEquals(0, jdbc.queryForObject("select count(*) from iskljucene_teme where predmet_id = ?", Integer.class,
                drugiPredmet.getId()));
    }

    @Test
    void opisMoraImatiOd1Do300Znakova() {
        assertEquals(NEISPRAVAN_OPIS, ishod(() -> iskljuci("a".repeat(301), null)));
        assertEquals(NEISPRAVAN_OPIS, ishod(() -> iskljuci(null, null)));
        assertEquals(NEISPRAVAN_OPIS, ishod(() -> iskljuci(" \t ", null)));
        assertEquals("400 Neispravan format podataka.", ishod(() -> service.dodaj(predmet.getId(), null, IzvorVarijante.RUCNO, null)));
        assertEquals(0, brojTema());

        // granica je u znakovima (code point), ne u UTF-16 jedinicama; razmaci na krajevima se ne broje
        String granica = "ž".repeat(298) + "😀→";
        assertEquals(granica, iskljuci("  " + granica + "  ", null).opis());
        assertEquals(granica, jdbc.queryForObject("select opis from iskljucene_teme where predmet_id = ?", String.class,
                predmet.getId()));
    }

    @Test
    void serijaVanObrascaJe400INistaSeNeUpisuje() {
        assertEquals("400 " + Serija.PORUKA, ishod(() -> service.dodaj(predmet.getId(),
                new IskljucenaTemaCmd("Pokretni zarez", null), IzvorVarijante.CLAUDE, "Uvoz")));
        assertEquals(0, brojTema());
    }

    @Test
    void nepoznatPredmetJe404() {
        assertEquals("404 Predmet nije pronađen.", ishod(() -> service.lista(Long.MAX_VALUE)));
        assertEquals("404 Predmet nije pronađen.", ishod(() -> service.dodaj(Long.MAX_VALUE,
                new IskljucenaTemaCmd("Pokretni zarez", null), IzvorVarijante.RUCNO, null)));
    }

    @Test
    void brisanjeGrupeOstavljaTemuBezGrupeIPutanje() {
        GrupaDetails r = koren(predmet, "Brojni sistemi");
        GrupaDetails a = podgrupa(r, "Razlomljeni brojevi");
        IskljucenaTemaInfo tema = iskljuci("Razlomljeni (decimalni) brojevi", a.id());

        grupaService.obrisi(a.id());

        List<IskljucenaTemaInfo> lista = service.lista(predmet.getId());
        assertEquals(1, lista.size());
        IskljucenaTemaInfo posle = lista.get(0);
        assertEquals(tema.id(), posle.id());
        assertNull(posle.grupaId());
        assertEquals(List.of(), posle.putanja());
        assertEquals("Razlomljeni (decimalni) brojevi", posle.opis());
        assertEquals(Map.of(), service.iskljuceneGrupe(predmet.getId()));
        assertEquals(mapa("Brojni sistemi", null), oznake());
    }

    @Test
    void obrisiUkljucujeTemuIDrugiPutJe404() {
        GrupaDetails a = koren(predmet, "Razlomljeni brojevi");
        IskljucenaTemaInfo tema = iskljuci("Razlomljeni brojevi", a.id());
        assertTrue(grupaService.detalji(a.id()).iskljucena());

        service.obrisi(tema.id());

        assertEquals(0, brojTema());
        assertFalse(grupaService.detalji(a.id()).iskljucena());
        assertNull(grupaService.detalji(a.id()).iskljucenjeId());
        assertEquals(mapa("Razlomljeni brojevi", null), oznake());
        assertEquals("404 Isključena tema nije pronađena.", ishod(() -> {
            service.obrisi(tema.id());
            return null;
        }));
        // posle uključivanja grupa može ponovo da se isključi
        assertTrue(ishod(() -> iskljuci("Razlomljeni brojevi", a.id())).startsWith("ok "));
    }

    @Test
    void istaTemaBezGrupeIstovremenoDajeJedanRed() throws Exception {
        // treća transakcija drži red predmeta, pa oba poziva stignu do ključa predmeta pre nego što ijedan prođe
        CountDownLatch drziPredmet = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        CompletableFuture<Void> drzac = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transakcije).executeWithoutResult(tx -> {
                    jdbc.queryForList("select id from predmeti where id = ? for update", predmet.getId());
                    drziPredmet.countDown();
                    await(pusti);
                }));
        assertTrue(drziPredmet.await(10, TimeUnit.SECONDS));

        List<CompletableFuture<String>> pozivi = List.of("Pokretni zarez", "pokretni  ZAREZ").stream()
                .map(opis -> CompletableFuture.supplyAsync(() -> ishod(() -> iskljuci(opis, null).id())))
                .toList();
        Thread.sleep(1500);
        assertTrue(pozivi.stream().noneMatch(CompletableFuture::isDone), "dodavanje mora čekati ključ predmeta");

        pusti.countDown();
        drzac.get(30, TimeUnit.SECONDS);
        List<String> ishodi = new ArrayList<>();
        for (CompletableFuture<String> poziv : pozivi) {
            ishodi.add(poziv.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1, ishodi.stream().filter(s -> s.startsWith("ok ")).count(), ishodi.toString());
        assertEquals(List.of(TEMA_ISKLJUCENA), ishodi.stream().filter(s -> !s.startsWith("ok ")).collect(Collectors.toList()));
        assertEquals(1, brojTema());
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
