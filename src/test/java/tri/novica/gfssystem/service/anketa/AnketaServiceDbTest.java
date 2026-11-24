package tri.novica.gfssystem.service.anketa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.anketa.AnketaDetails;
import tri.novica.gfssystem.dto.anketa.AnketaStudentu;
import tri.novica.gfssystem.dto.anketa.GlasCmd;
import tri.novica.gfssystem.dto.anketa.PokreniAnketuCmd;
import tri.novica.gfssystem.dto.anketa.SablonAnkete;
import tri.novica.gfssystem.dto.anketa.SablonAnketeCmd;
import tri.novica.gfssystem.dto.portal.PodesavanjaNaloga;
import tri.novica.gfssystem.entity.anketa.IzvorAnkete;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.PredavanjeService;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis;
import tri.novica.gfssystem.service.push.LazniPushTransport;
import tri.novica.gfssystem.service.push.LazniUredjaj;
import tri.novica.gfssystem.service.push.PushPretplate;
import tri.novica.gfssystem.service.push.PushTransport;
import tri.novica.gfssystem.service.push.StudentskaObavestenja;
import tri.novica.gfssystem.service.raspored.RasporedSatConfig;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Brza anketa nad pravom MySQL bazom (spec brze ankete 4, 7 i 9), bez {@code @Transactional}: ko sme da glasa, dvostruki
 * dodir paralelno, promena glasa, zatvaranje, nova anketa na času i kraj predavanja, brojevi za Novicu, skup za studenta,
 * granice, šabloni i push sa zvonom. Sat je pomeren u 2033 ({@link RasporedSatConfig}), pa je "danas" samo ovde; isti
 * {@code @Import} kao {@code PushDbTest} (isti kontekst).
 */
@SpringBootTest
@Import({RasporedSatConfig.class, LazniMejlServis.class, LazniPushTransport.class})
class AnketaServiceDbTest {

    static final LocalDateTime SADA = LocalDateTime.of(2033, 2, 10, 10, 0);
    static final Duration ROK = Duration.ofSeconds(10);
    static final List<String> DA_NE = List.of("Da", "Ne");

    @Autowired AnketaService ankete;
    @Autowired AnketaSablonService sabloni;
    @Autowired PredavanjeService predavanja;
    @Autowired PodesavanjaStudentaServis podesavanja;
    @Autowired StudentskaObavestenja obavestenja;
    @Autowired PushPretplate pretplate;
    @Autowired LazniPushTransport transport;
    @Autowired RasporedSatConfig.Sat sat;
    @Autowired JdbcTemplate jdbc;

    CasPodaci d;
    Long grupa;
    Long drugaGrupa;
    Long predavanje;
    Long ana;
    Long bora;
    Long stariji;
    Long tudja;
    final List<Long> sabloniTesta = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sat.postavi(SADA);
        transport.ocisti();
        d = new CasPodaci(jdbc);
        grupa = d.grupa(2032);
        drugaGrupa = d.grupa(2031);
        predavanje = d.predavanje(grupa, SADA.toLocalDate());
        ana = d.student(grupa, "Ana");
        bora = d.student(grupa, "Bora");
        stariji = d.student(drugaGrupa, "Ceda");
        tudja = d.student(drugaGrupa, "Daca");
        cekiraj(predavanje, ana);
        cekiraj(predavanje, stariji);
    }

    @AfterEach
    void tearDown() {
        for (Long id : sabloniTesta) jdbc.update("delete from ankete_sabloni where id = ?", id);
        jdbc.update("delete from studentski_uredjaji where student_id in (select id from studenti where grupa_id in (?, ?))",
                grupa, drugaGrupa);
        d.obrisi();
        transport.ocisti();
    }

    // ---------------------------------------------------------------- glas

    @Test
    void naCasuGlasajuSamoCekiraniIGlasSeMenja() {
        AnketaDetails a = naCasu("Pauza?", null);
        assertTrue(a.otvorena());
        assertEquals(PublikaAnkete.NA_CASU, a.publika());
        assertEquals(IzvorAnkete.NASTAVNIK, a.izvor());
        assertEquals(grupa, a.grupaId(), "grupa predavanja");
        assertEquals("Čas " + d.oznaka, a.predmetNaziv());
        assertEquals(List.of(0, 0), a.glasovi());
        assertEquals(2, a.brojMogucih(), "čekirani: Ana i stariji student druge grupe");
        assertNull(a.rok());

        AnketaStudentu g = ankete.glasaj(ana, a.id(), glas(0));
        assertEquals(List.of(0), g.mojGlas());
        assertNull(g.rezultat(), "rezultat tek kad ga Novica prikaže");
        assertTrue(g.otvorena());
        g = ankete.glasaj(ana, a.id(), glas(1));
        assertEquals(List.of(1), g.mojGlas());
        assertEquals(1, brojGlasova(a.id()));
        ankete.glasaj(stariji, a.id(), glas(1));

        ocekuj(403, AnketaService.NIJE_ZA_TEBE, () -> ankete.glasaj(bora, a.id(), glas(0)));
        ocekuj(404, AnketaService.NIJE_PRONADJENA, () -> ankete.glasaj(ana, -1L, glas(0)));
        ocekuj(400, AnketaPravila.JEDAN_ODGOVOR, () -> ankete.glasaj(ana, a.id(), glas(0, 1)));
        ocekuj(400, AnketaPravila.NEISPRAVAN_ODGOVOR, () -> ankete.glasaj(ana, a.id(), glas(2)));
        ocekuj(400, AnketaPravila.JEDAN_ODGOVOR, () -> ankete.glasaj(ana, a.id(), null));

        AnketaDetails z = ankete.zatvori(a.id());
        assertFalse(z.otvorena());
        assertEquals(SADA, z.zatvoreno());
        assertEquals(List.of(0, 2), z.glasovi());
        assertEquals(2, z.brojGlasalo());
        ocekuj(409, AnketaService.ZATVORENA, () -> ankete.glasaj(ana, a.id(), glas(0)));
        ocekuj(403, AnketaService.NIJE_ZA_TEBE, () -> ankete.glasaj(bora, a.id(), glas(0)));
        sat.postavi(SADA.plusMinutes(1));
        assertEquals(SADA, ankete.zatvori(a.id()).zatvoreno(), "ponovno zatvaranje ne menja vreme");
    }

    @Test
    void viseOdgovoraZbirPoOpciji() {
        AnketaDetails a = ankete.pokreni(new PokreniAnketuCmd(null, "Termini?", List.of("Pon", "Uto", "Sre"), true,
                PublikaAnkete.NA_CASU, predavanje, null, null, null), IzvorAnkete.NASTAVNIK);
        ankete.glasaj(ana, a.id(), glas(2, 0));
        ankete.glasaj(stariji, a.id(), glas(0));
        ocekuj(400, AnketaPravila.BAR_JEDAN_ODGOVOR, () -> ankete.glasaj(ana, a.id(), glas()));
        AnketaDetails det = ankete.detalji(a.id());
        assertEquals(List.of(2, 0, 1), det.glasovi());
        assertEquals(2, det.brojGlasalo(), "broji studente, ne izbore");
        assertEquals("0,2", jdbc.queryForObject("select opcije from ankete_glasovi where anketa_id = ? and student_id = ?",
                String.class, a.id(), ana));
    }

    @Test
    void dvostrukiDodirParalelnoJedanRed() throws Exception {
        AnketaDetails a = naCasu("Tempo?", null);
        ExecutorService niti = Executors.newFixedThreadPool(4);
        try {
            for (int krug = 0; krug < 5; krug++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<AnketaStudentu>> f = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    int izbor = i % 2;
                    f.add(niti.submit(() -> {
                        start.await();
                        return ankete.glasaj(ana, a.id(), glas(izbor));
                    }));
                }
                start.countDown();
                for (Future<AnketaStudentu> x : f) assertNotNull(x.get().mojGlas());
                assertEquals(1, brojGlasova(a.id()), "krug " + krug);
            }
        } finally {
            niti.shutdownNow();
        }
        assertEquals(1, ankete.detalji(a.id()).brojGlasalo());
    }

    @Test
    void grupaGlasajuGrupaIStarijiCekiraniNaPredavanju() {
        AnketaDetails a = zaGrupu("Kolokvijum?", grupa, predavanje, SADA.plusDays(1));
        assertTrue(a.otvorena());
        assertEquals(predavanje, a.predavanjeId());
        assertEquals(3, a.brojMogucih(), "Ana, Bora i stariji čekiran na predavanju");
        ankete.glasaj(bora, a.id(), glas(0));
        ankete.glasaj(stariji, a.id(), glas(1));
        ocekuj(403, AnketaService.NIJE_ZA_TEBE, () -> ankete.glasaj(tudja, a.id(), glas(0)));

        AnketaDetails bez = zaGrupu("Konsultacije?", grupa, null, SADA.plusDays(1));
        assertNull(bez.predavanjeId());
        assertEquals(2, bez.brojMogucih());
        ankete.glasaj(bora, bez.id(), glas(0));
        ocekuj(403, AnketaService.NIJE_ZA_TEBE, () -> ankete.glasaj(stariji, bez.id(), glas(0)));

        sat.postavi(SADA.plusDays(1));
        ocekuj(409, AnketaService.ZATVORENA, () -> ankete.glasaj(bora, a.id(), glas(1)));
        assertEquals(SADA.plusDays(1), ankete.detalji(a.id()).zatvoreno(), "efektivno zatvaranje = rok");
    }

    @Test
    void brojMogucihNikadIspodBrojaGlasalo() {
        AnketaDetails a = naCasu("Jasno?", null);
        ankete.glasaj(stariji, a.id(), glas(0));
        jdbc.update("delete from cekiranja where predavanje_id = ? and student_id = ?", predavanje, stariji);
        AnketaDetails det = ankete.detalji(a.id());
        assertEquals(1, det.brojGlasalo());
        assertEquals(2, det.brojMogucih(), "glasao pa skinut sa časa i dalje se broji");
    }

    // ---------------------------------------------------------------- pokretanje i zatvaranje

    @Test
    void novaAnketaNaCasuZatvaraStaruIKrajPredavanjaZatvaraSamoNaCasu() {
        AnketaDetails prva = naCasu("Prva?", null);
        sat.postavi(SADA.plusSeconds(30));
        AnketaDetails druga = naCasu("Druga?", 60);
        assertEquals(SADA.plusSeconds(90), druga.rok());
        assertNotNull(druga.rokMs());
        AnketaDetails p = ankete.detalji(prva.id());
        assertFalse(p.otvorena());
        assertEquals(SADA.plusSeconds(30), p.zatvoreno());
        AnketaDetails grupna = zaGrupu("Grupa?", null, predavanje, SADA.plusDays(2));
        assertEquals(grupa, grupna.grupaId(), "grupa iz predavanja");
        assertEquals(List.of(druga.id(), grupna.id()).stream().sorted().toList(),
                ankete.aktivne(predavanje, null).stream().map(AnketaDetails::id).sorted().toList());

        sat.postavi(SADA.plusSeconds(40));
        predavanja.zavrsiPredavanje(predavanje);
        AnketaDetails posle = ankete.detalji(druga.id());
        assertFalse(posle.otvorena());
        assertEquals(SADA.plusSeconds(40), posle.zatvoreno(), "kraj predavanja upisuje pravo vreme");
        assertTrue(ankete.detalji(grupna.id()).otvorena(), "anketa za grupu traje do roka");
        assertNull(jdbc.queryForObject("select zatvoreno from ankete where id = ?", Timestamp.class, grupna.id()));
        ocekuj(409, AnketaService.PREDAVANJE_NIJE_U_TOKU, () -> naCasu("Posle kraja?", null));
    }

    @Test
    void greskePokretanja() {
        ocekuj(400, AnketaService.IZABERI_PREDAVANJE, () -> ankete.pokreni(new PokreniAnketuCmd(null, "P?", DA_NE, false,
                null, null, null, null, null), IzvorAnkete.NASTAVNIK));
        ocekuj(404, AnketaService.PREDAVANJE_NIJE_PRONADJENO, () -> ankete.pokreni(new PokreniAnketuCmd(null, "P?", DA_NE,
                false, PublikaAnkete.NA_CASU, -1L, null, null, null), IzvorAnkete.NASTAVNIK));
        Long bezGrupe = d.predavanje(null, SADA.toLocalDate());
        ocekuj(409, AnketaService.PREDAVANJE_BEZ_GRUPE, () -> ankete.pokreni(new PokreniAnketuCmd(null, "P?", DA_NE, false,
                PublikaAnkete.NA_CASU, bezGrupe, null, null, null), IzvorAnkete.NASTAVNIK));
        ocekuj(400, AnketaPravila.TRAJANJE, () -> naCasu("P?", 5));
        ocekuj(400, AnketaPravila.BROJ_ODGOVORA, () -> ankete.pokreni(new PokreniAnketuCmd(null, "P?", List.of("Da"), false,
                PublikaAnkete.NA_CASU, predavanje, null, null, null), IzvorAnkete.NASTAVNIK));
        ocekuj(400, AnketaService.IZABERI_ROK, () -> zaGrupu("G?", grupa, null, null));
        ocekuj(400, AnketaService.ROK, () -> zaGrupu("G?", grupa, null, SADA));
        ocekuj(400, AnketaService.ROK, () -> zaGrupu("G?", grupa, null, SADA.plusDays(14).plusSeconds(1)));
        zaGrupu("Tačno 14 dana", grupa, null, SADA.plusDays(14));
        ocekuj(400, AnketaService.IZABERI_GRUPU, () -> zaGrupu("G?", null, null, SADA.plusDays(1)));
        ocekuj(404, AnketaService.GRUPA_NIJE_PRONADJENA, () -> zaGrupu("G?", -1L, null, SADA.plusDays(1)));
        ocekuj(400, AnketaService.DRUGA_GRUPA, () -> zaGrupu("G?", drugaGrupa, predavanje, SADA.plusDays(1)));
        ocekuj(404, AnketaService.SABLON_NIJE_PRONADJEN, () -> ankete.pokreni(new PokreniAnketuCmd(-1L, "P?", DA_NE, false,
                PublikaAnkete.NA_CASU, predavanje, null, null, null), IzvorAnkete.NASTAVNIK));
        assertEquals(1, brojAnketa(), "neuspela pokretanja ne ostavljaju ništa (ni zatvorenu prethodnu)");

        for (int i = 2; i <= AnketaService.NAJVISE_OTVORENIH_ZA_GRUPU; i++) zaGrupu("G" + i, grupa, null, SADA.plusDays(1));
        ocekuj(409, AnketaService.PREVISE_ZA_GRUPU, () -> zaGrupu("Šesta", grupa, null, SADA.plusDays(1)));
        zaGrupu("Druga grupa može", drugaGrupa, null, SADA.plusDays(1));
        ocekuj(400, AnketaService.PREDAVANJE_ILI_GRUPA, () -> ankete.aktivne(null, null));
    }

    @Test
    void sablonDobijaBrojacABrisanjeOstavljaAnkete() {
        SablonAnkete s = sablon("Da li vam treba pauza?", PublikaAnkete.NA_CASU);
        AnketaDetails a = ankete.pokreni(new PokreniAnketuCmd(s.id(), "Pauza, ali izmenjeno?", DA_NE, false,
                PublikaAnkete.NA_CASU, predavanje, null, null, null), IzvorAnkete.NASTAVNIK);
        assertEquals(s.id(), a.sablonId());
        SablonAnkete posle = sabloni.sablon(s.id());
        assertEquals(1, posle.brojPokretanja());
        assertEquals(SADA, posle.poslednjePokretanje());
        assertEquals("Da li vam treba pauza?", posle.pitanje(), "šablon se pri pokretanju ne menja");

        sabloni.obrisi(s.id());
        AnketaDetails ostala = ankete.detalji(a.id());
        assertNull(ostala.sablonId());
        assertEquals("Pauza, ali izmenjeno?", ostala.pitanje());
        ankete.obrisi(a.id());
        ocekuj(404, AnketaService.NIJE_PRONADJENA, () -> ankete.detalji(a.id()));
    }

    @Test
    void klodPokreceNaZivomPredavanjuIzSablona() {
        SablonAnkete s = sabloni.kreiraj(new SablonAnketeCmd(null, "Kakav je tempo?", List.of("Prebrzo", "Taman", "Sporo"),
                false, PublikaAnkete.NA_CASU, 60, 24, true, "⏱️", null, "Tempo", true));
        sabloniTesta.add(s.id());
        assertNull(s.rokSati(), "rokSati važi samo za grupu");
        AnketaDetails a = ankete.pokreniZaKloda(new PokreniAnketuCmd(s.id(), null, null, null, null, null, null, null,
                null), null);
        assertEquals(IzvorAnkete.CLAUDE, a.izvor());
        assertEquals(predavanje, a.predavanjeId(), "jedino živo predavanje danas");
        assertEquals(List.of("Prebrzo", "Taman", "Sporo"), a.opcije());
        assertEquals(SADA.plusSeconds(60), a.rok());

        AnketaDetails g = ankete.pokreniZaKloda(new PokreniAnketuCmd(null, "Dodatni čas?", DA_NE, null, PublikaAnkete.GRUPA,
                null, grupa, null, null), 48);
        assertEquals(SADA.plusHours(48), g.rok());
        assertEquals(IzvorAnkete.CLAUDE, g.izvor());

        Long drugo = d.predavanje(drugaGrupa, SADA.toLocalDate());
        SystemException e = assertThrows(SystemException.class, () -> ankete.pokreniZaKloda(new PokreniAnketuCmd(null,
                "P?", DA_NE, null, null, null, null, null, null), null));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains(drugo + ": ") && e.getMessage().contains(predavanje + ": "), e.getMessage());
        d.zavrsi(drugo);
        predavanja.zavrsiPredavanje(predavanje);
        ocekuj(409, AnketaService.NEMA_ZIVOG, () -> ankete.pokreniZaKloda(new PokreniAnketuCmd(null, "P?", DA_NE, null,
                null, null, null, null, null), null));
    }

    // ---------------------------------------------------------------- student

    @Test
    void skupZaStudentaProzoriIRezultat() {
        AnketaDetails a = naCasu("Jasno?", null);
        assertEquals(List.of(a.id()), ids(ankete.zaStudenta(ana)));
        assertEquals(List.of(), ids(ankete.zaStudenta(bora)), "nije čekiran");
        assertEquals(List.of(a.id()), ids(ankete.zaStudenta(stariji)));
        ankete.glasaj(ana, a.id(), glas(0));
        ankete.zatvori(a.id());
        assertEquals(List.of(), ids(ankete.zaStudenta(ana)), "zatvorena bez rezultata nestaje");
        ankete.prikaziRezultat(a.id(), true);
        AnketaStudentu s = ankete.zaStudenta(ana).getFirst();
        assertFalse(s.otvorena());
        assertEquals(List.of(0), s.mojGlas());
        assertEquals(List.of(1, 0), s.rezultat().glasovi());
        assertEquals(1, s.rezultat().brojGlasalo());
        assertNull(ankete.zaStudenta(stariji).getFirst().mojGlas());

        sat.postavi(SADA.plusHours(2).plusSeconds(1));
        assertEquals(List.of(), ids(ankete.zaStudenta(ana)), "posle 2 h");

        sat.postavi(SADA.plusHours(3));
        AnketaDetails g = zaGrupu("Datum?", grupa, null, SADA.plusHours(4));
        ankete.prikaziRezultat(g.id(), true);
        assertNotNull(ankete.zaStudenta(bora).getFirst().rezultat(), "rezultat i dok je otvorena");
        sat.postavi(SADA.plusHours(4).plusHours(23));
        assertEquals(List.of(g.id()), ids(ankete.zaStudenta(bora)), "grupa: 24 h posle roka");
        sat.postavi(SADA.plusHours(4).plusHours(24).plusSeconds(1));
        assertEquals(List.of(), ids(ankete.zaStudenta(bora)));
    }

    @Test
    void studentuNajvisePetNajnovijih() {
        List<Long> redom = new ArrayList<>();
        for (int i = 0; i < AnketaService.NAJVISE_OTVORENIH_ZA_GRUPU; i++) {
            sat.postavi(SADA.plusSeconds(i));
            redom.add(zaGrupu("G" + i, grupa, null, SADA.plusDays(1)).id());
        }
        sat.postavi(SADA.plusSeconds(10));
        redom.add(naCasu("Na času", null).id());
        List<Long> ocekivano = new ArrayList<>(redom.reversed().subList(0, AnketaService.NAJVISE_ZA_STUDENTA));
        assertEquals(ocekivano, ids(ankete.zaStudenta(ana)));
        assertEquals(redom.reversed().subList(1, 6), ids(ankete.zaStudenta(bora)), "Bora nije na času: samo grupne");
    }

    // ---------------------------------------------------------------- zvono i push

    @Test
    void zvonoIPushPoVrstiSamoSaUkljucenimPrekidacem() throws Exception {
        LazniUredjaj anin = pretplati(ana);
        LazniUredjaj borin = pretplati(bora);
        podesavanja.sacuvaj(bora, new PodesavanjaNaloga(true, null, null, null, null, null, null, false));
        cekiraj(predavanje, bora);

        AnketaDetails a = naCasu("  Da li vam treba pauza?  ", 60);
        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        Thread.sleep(500);
        z = transport.primljeni();
        assertEquals(1, z.size(), "Bora je isključio push za ankete");
        PushTransport.Zahtev zahtev = z.getFirst();
        assertTrue(anin.prima(zahtev));
        assertTrue(zahtev.hitno());
        assertEquals(60, zahtev.ttlSekundi());
        assertEquals("{\"naslov\":\"Anketa: Da li vam treba pauza?\",\"tekst\":\"Dodirni i glasaj.\",\"putanja\":\"nalog/cas\","
                + "\"oznaka\":\"anketa-cas\"}", anin.desifruj(zahtev));
        assertEquals(List.of("ANKETA_CAS"), vrste(ana, a.id()));
        assertEquals(List.of("ANKETA_CAS"), vrste(bora, a.id()), "red zvona i bez push-a");
        assertTrue(obavestenja.zvono(ana).stavke().isEmpty(), "anketa na času se ne vidi u zvonu");

        transport.ocisti();
        AnketaDetails g = zaGrupu("Kolokvijum?", grupa, null, SADA.plusDays(3));
        z = transport.cekaj(1, ROK);
        Thread.sleep(500);
        z = transport.primljeni();
        assertEquals(1, z.size());
        zahtev = z.getFirst();
        assertTrue(anin.prima(zahtev));
        assertFalse(borin.prima(zahtev));
        assertFalse(zahtev.hitno());
        assertEquals(Duration.ofHours(24).toSeconds(), zahtev.ttlSekundi(), "najviše 24 h");
        String telo = anin.desifruj(zahtev);
        assertTrue(telo.contains("\"tekst\":\"Glasaj do ned 13.2. u 10:00.\""), telo);
        assertTrue(telo.contains("\"oznaka\":\"anketa-" + g.id() + "\""), telo);
        assertEquals(List.of("ANKETA"), vrste(bora, g.id()));
        assertEquals(1, obavestenja.zvono(bora).stavke().size(), "anketa za grupu je u zvonu");
        assertFalse(telo.contains("Ana") || telo.contains("Bora"));
    }

    // ---------------------------------------------------------------- pomoćno

    AnketaDetails naCasu(String pitanje, Integer trajanje) {
        return ankete.pokreni(new PokreniAnketuCmd(null, pitanje, DA_NE, false, PublikaAnkete.NA_CASU, predavanje, null,
                trajanje, null), IzvorAnkete.NASTAVNIK);
    }

    AnketaDetails zaGrupu(String pitanje, Long grupaId, Long predavanjeId, LocalDateTime rok) {
        return ankete.pokreni(new PokreniAnketuCmd(null, pitanje, DA_NE, false, PublikaAnkete.GRUPA, predavanjeId, grupaId,
                null, rok), IzvorAnkete.NASTAVNIK);
    }

    SablonAnkete sablon(String pitanje, PublikaAnkete publika) {
        SablonAnkete s = sabloni.kreiraj(new SablonAnketeCmd(null, pitanje, DA_NE, false, publika, null, null, false, null,
                null, null, false));
        sabloniTesta.add(s.id());
        return s;
    }

    static GlasCmd glas(Integer... izbor) {
        return new GlasCmd(List.of(izbor));
    }

    static List<Long> ids(List<AnketaStudentu> lista) {
        return lista.stream().map(AnketaStudentu::id).toList();
    }

    void cekiraj(Long predavanjeId, Long studentId) {
        jdbc.update("insert into cekiranja (predavanje_id, student_id, vreme) values (?, ?, ?)", predavanjeId, studentId,
                Timestamp.valueOf(SADA.minusMinutes(5)));
    }

    int brojGlasova(Long anketaId) {
        return jdbc.queryForObject("select count(*) from ankete_glasovi where anketa_id = ?", Integer.class, anketaId);
    }

    int brojAnketa() {
        return jdbc.queryForObject("select count(*) from ankete where grupa_id in (?, ?)", Integer.class, grupa, drugaGrupa);
    }

    List<String> vrste(Long studentId, Long anketaId) {
        return jdbc.queryForList("select vrsta from studentska_obavestenja where student_id = ? and kljuc = ?", String.class,
                studentId, "ANKETA:" + anketaId);
    }

    LazniUredjaj pretplati(Long studentId) {
        String token = TokenHash.od(UUID.randomUUID().toString());
        jdbc.update("insert into studentski_uredjaji (student_id, token_hes, opis, nacin, prvi_ulaz, poslednji_ulaz, istice, opozvan) "
                        + "values (?, ?, 'JUnit', 'KOD', ?, ?, ?, null)", studentId, token, Timestamp.valueOf(SADA),
                Timestamp.valueOf(SADA), Timestamp.valueOf(SADA.plusDays(365)));
        long u = jdbc.queryForObject("select id from studentski_uredjaji where token_hes = ?", Long.class, token);
        LazniUredjaj l = new LazniUredjaj();
        pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(u), l.cmd());
        return l;
    }

    static void ocekuj(int kod, String poruka, Executable e) {
        SystemException ex = assertThrows(SystemException.class, e);
        assertEquals(kod, ex.getCode(), poruka);
        assertEquals(poruka, ex.getMessage());
    }
}
