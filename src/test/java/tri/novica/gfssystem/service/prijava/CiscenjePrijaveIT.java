package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Čišćenje starih zapisa prijave (spec 9): svaka tabela ima rok, red tik unutar roka ostaje, red tik izvan njega se briše;
 * {@code Clock} se pomera, pa se ne čeka. Redovi se prave pravo u bazi (JDBC) sa nasumičnim oznakama, a proverava se samo
 * ono što je test napravio.
 */
@SpringBootTest
@Import({PrijavaSatConfig.class, LazniMejlServis.class})
class CiscenjePrijaveIT {

    @Autowired CiscenjePrijave ciscenje;
    @Autowired ObavestenjeServis obavestenjaServis;
    @Autowired KodServis kodServis;
    @Autowired StudentRepository studentRepository;
    @Autowired LazniMejlServis mejl;
    @Autowired PomerljivSat sat;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;
    LocalDateTime sada;
    Long registracija;
    final List<String> kljucevi = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sat.postavi(Instant.now());
        sada = LocalDateTime.now(sat);
        mejl.ocisti();
        studenti = new TestStudenti(studentRepository, jdbc);
        ciscenje.velicinaGrupe(5000);
    }

    @AfterEach
    void ocisti() {
        ciscenje.velicinaGrupe(5000);
        mejl.ocisti();
        studenti.obrisi();
        jdbc.update("delete from obavestenja");
        for (String k : kljucevi) {
            jdbc.update("delete from pokusaji where kljuc = ?", k);
        }
        if (registracija != null) {
            jdbc.update("delete from lti_registracije where id = ?", registracija);
        }
    }

    // ---------------------------------------------------------------- pomoćnici

    static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    long ubaci(String sql, Object... argumenti) {
        GeneratedKeyHolder kljuc = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < argumenti.length; i++) {
                ps.setObject(i + 1, argumenti[i]);
            }
            return ps;
        }, kljuc);
        return kljuc.getKey().longValue();
    }

    boolean postoji(String tabela, long id) {
        return jdbc.queryForObject("select count(*) from " + tabela + " where id = ?", Integer.class, id) == 1;
    }

    static String slucajan() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    long pokusaj(VrstaPokusaja vrsta, Duration staro) {
        String kljuc = "t9-" + slucajan().substring(0, 12);
        kljucevi.add(kljuc);
        return ubaci("insert into pokusaji (vrsta, kljuc, vreme) values (?, ?, ?)", vrsta.name(), kljuc, ts(sada.minus(staro)));
    }

    long kod(Student s, Duration staro) {
        LocalDateTime kreirano = sada.minus(staro);
        return ubaci("insert into kodovi_za_prijavu (student_id, kod_hes, so, kreirano, istice, pogresni) values (?, ?, ?, ?, ?, 0)",
                s.getId(), TokenHash.od(slucajan()), slucajan(), ts(kreirano), ts(kreirano.plusMinutes(10)));
    }

    long registracija() {
        if (registracija == null) {
            registracija = ubaci("insert into lti_registracije (naziv, izdavac, client_id, deployment_id, auth_url, jwks_url, aktivna, kreirano) "
                            + "values ('T9', ?, 'c', 'd', 'https://x.example.test/a', 'https://x.example.test/j', 1, ?)",
                    "https://t9-" + slucajan().substring(0, 10) + ".example.test", ts(sada));
        }
        return registracija;
    }

    /** Stanje koje ističe {@code istekaPre} u prošlosti (negativno = u budućnosti). */
    long stanje(Duration istekaPre, Duration iskorisceno) {
        LocalDateTime istice = sada.minus(istekaPre);
        return ubaci("insert into lti_stanja (stanje_hes, nonce, kolacic_hes, registracija_id, kreirano, istice, iskorisceno) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                TokenHash.od(slucajan()), slucajan().substring(0, 32), TokenHash.od(slucajan()), registracija(),
                ts(istice.minusMinutes(10)), ts(istice), iskorisceno == null ? null : ts(sada.minus(iskorisceno)));
    }

    long povezivanje(Duration istekaPre, Duration iskorisceno) {
        LocalDateTime istice = sada.minus(istekaPre);
        return ubaci("insert into lti_povezivanja (token_hes, registracija_id, subjekat, istice, iskorisceno) values (?, ?, ?, ?, ?)",
                TokenHash.od(slucajan()), registracija(), "sub-" + slucajan().substring(0, 8), ts(istice),
                iskorisceno == null ? null : ts(sada.minus(iskorisceno)));
    }

    long sesija(Duration istekaPre, Duration opozvana) {
        LocalDateTime istice = sada.minus(istekaPre);
        return ubaci("insert into nastavnicke_sesije (token_hes, opis, ip, kreirano, poslednji_ulaz, istice, opozvana) values (?, 'JUnit', 'x', ?, ?, ?, ?)",
                TokenHash.od(slucajan()), ts(istice.minusDays(90)), ts(istice.minusDays(1)), ts(istice),
                opozvana == null ? null : ts(sada.minus(opozvana)));
    }

    long uredjaj(Student s, Duration istekaPre, Duration opozvan) {
        LocalDateTime istice = sada.minus(istekaPre);
        return ubaci("insert into studentski_uredjaji (student_id, token_hes, opis, nacin, prvi_ulaz, poslednji_ulaz, istice, opozvan) "
                        + "values (?, ?, 'JUnit', 'KOD', ?, ?, ?, ?)",
                s.getId(), TokenHash.od(slucajan()), ts(istice.minusDays(365)), ts(istice.minusDays(365)), ts(istice),
                opozvan == null ? null : ts(sada.minus(opozvan)));
    }

    long obavestenje(Student s, String vrsta, Duration kreirano, Duration reseno) {
        return ubaci("insert into obavestenja (vrsta, student_id, podaci, kreirano, procitano, reseno) values (?, ?, '{}', ?, ?, ?)",
                vrsta, s == null ? null : s.getId(), ts(sada.minus(kreirano)),
                reseno == null ? null : ts(sada.minus(reseno)), reseno == null ? null : ts(sada.minus(reseno)));
    }

    // ---------------------------------------------------------------- raspored

    @Test
    void radiJednomNaSatUSedmomMinutu() throws Exception {
        Scheduled s = CiscenjePrijave.class.getMethod("ocisti").getAnnotation(Scheduled.class);
        assertNotNull(s, "ocisti() mora biti @Scheduled");
        assertEquals("0 7 * * * *", s.cron());
    }

    // ---------------------------------------------------------------- rokovi po tabelama

    @Test
    void pokusajiStarijiOdDvaDanaSeBrisu() {
        long svez = pokusaj(VrstaPokusaja.KOD_SLANJE_IP, Duration.ofMinutes(5));
        long skoroDvaDana = pokusaj(VrstaPokusaja.KOD_GRESKA_IP, Duration.ofHours(47).plusMinutes(50));
        long preko = pokusaj(VrstaPokusaja.POMOC_IP, Duration.ofHours(48).plusMinutes(10));
        long stari = pokusaj(VrstaPokusaja.NASTAVNIK_GRESKA_IP, Duration.ofDays(30));

        ciscenje.ocisti();

        assertTrue(postoji("pokusaji", svez));
        assertTrue(postoji("pokusaji", skoroDvaDana));
        assertFalse(postoji("pokusaji", preko));
        assertFalse(postoji("pokusaji", stari));
    }

    @Test
    void kodoviStarijiOdJedanDanSeBrisu() {
        Student s = studenti.napravi("ana@example.test");
        long svez = kod(s, Duration.ofMinutes(20));
        long skoroDan = kod(s, Duration.ofHours(23).plusMinutes(50));
        long preko = kod(s, Duration.ofHours(24).plusMinutes(10));

        ciscenje.ocisti();

        assertTrue(postoji("kodovi_za_prijavu", svez));
        assertTrue(postoji("kodovi_za_prijavu", skoroDan));
        assertFalse(postoji("kodovi_za_prijavu", preko));
    }

    @Test
    void istekliLtiStanjaSeBrisuBezCekanjaJednogDana() {
        long vazece = stanje(Duration.ofMinutes(-9), null);
        long istekloPreMalo = stanje(Duration.ofMinutes(30), null);
        long istekloPreSatIPo = stanje(Duration.ofMinutes(70), null);
        long istekloPreDanas = stanje(Duration.ofHours(30), null);
        // iskorišćeno, ali još važi: ostaje dok ne istekne (jednokratnost se čita iz reda)
        long iskoriscenoJosVazi = stanje(Duration.ofMinutes(-5), Duration.ofMinutes(1));
        long iskoriscenoIIsteklo = stanje(Duration.ofHours(3), Duration.ofHours(3));

        ciscenje.ocisti();

        assertTrue(postoji("lti_stanja", vazece));
        assertTrue(postoji("lti_stanja", istekloPreMalo));
        assertFalse(postoji("lti_stanja", istekloPreSatIPo));
        assertFalse(postoji("lti_stanja", istekloPreDanas));
        assertTrue(postoji("lti_stanja", iskoriscenoJosVazi));
        assertFalse(postoji("lti_stanja", iskoriscenoIIsteklo));
    }

    @Test
    void istekliIIskorisceniLtiPovezivanjaSeBrisu() {
        long vazece = povezivanje(Duration.ofMinutes(-25), null);
        long istekloPreMalo = povezivanje(Duration.ofMinutes(30), null);
        long isteklo = povezivanje(Duration.ofHours(2), null);
        long iskoriscenoPreMalo = povezivanje(Duration.ofMinutes(-20), Duration.ofMinutes(5));
        long iskoriscenoPre2h = povezivanje(Duration.ofMinutes(-20), Duration.ofHours(2));

        ciscenje.ocisti();

        assertTrue(postoji("lti_povezivanja", vazece));
        assertTrue(postoji("lti_povezivanja", istekloPreMalo));
        assertFalse(postoji("lti_povezivanja", isteklo));
        assertTrue(postoji("lti_povezivanja", iskoriscenoPreMalo));
        assertFalse(postoji("lti_povezivanja", iskoriscenoPre2h));
    }

    @Test
    void nastavnickeSesijeIstekleIliOpozvaneVisePutOdTridesetDanaSeBrisu() {
        long vazeca = sesija(Duration.ofDays(-30), null);
        long istekla29 = sesija(Duration.ofDays(29), null);
        long istekla31 = sesija(Duration.ofDays(31), null);
        long opozvanaJuce = sesija(Duration.ofDays(-30), Duration.ofDays(1));
        long opozvana29 = sesija(Duration.ofDays(-30), Duration.ofDays(29));
        long opozvana31 = sesija(Duration.ofDays(-30), Duration.ofDays(31));

        ciscenje.ocisti();

        assertTrue(postoji("nastavnicke_sesije", vazeca));
        assertTrue(postoji("nastavnicke_sesije", istekla29));
        assertFalse(postoji("nastavnicke_sesije", istekla31));
        assertTrue(postoji("nastavnicke_sesije", opozvanaJuce));
        assertTrue(postoji("nastavnicke_sesije", opozvana29));
        assertFalse(postoji("nastavnicke_sesije", opozvana31));
    }

    @Test
    void studentskiUredjajiIstekliIliOpozvaniVisePutOdTridesetDanaSeBrisu() {
        Student s = studenti.napravi("ana@example.test");
        long vazeci = uredjaj(s, Duration.ofDays(-300), null);
        long istekao29 = uredjaj(s, Duration.ofDays(29), null);
        long istekao31 = uredjaj(s, Duration.ofDays(31), null);
        long opozvanJuce = uredjaj(s, Duration.ofDays(-300), Duration.ofDays(1));
        long opozvan31 = uredjaj(s, Duration.ofDays(-300), Duration.ofDays(31));

        ciscenje.ocisti();

        assertTrue(postoji("studentski_uredjaji", vazeci));
        assertTrue(postoji("studentski_uredjaji", istekao29));
        assertFalse(postoji("studentski_uredjaji", istekao31));
        assertTrue(postoji("studentski_uredjaji", opozvanJuce));
        assertFalse(postoji("studentski_uredjaji", opozvan31));
    }

    @Test
    void resenaObavestenjaStarijaOdSto80DanaSeBrisuOtvorenaNikad() {
        Student s = studenti.napravi("ana@example.test");
        long resenoPre179 = obavestenje(s, "NE_STIZE_KOD", Duration.ofDays(200), Duration.ofDays(179));
        long resenoPre181 = obavestenje(s, "NE_STIZE_KOD", Duration.ofDays(200), Duration.ofDays(181));
        long otvorenoStaro = obavestenje(s, "NE_STIZE_KOD", Duration.ofDays(400), null);
        long bezStudenta = obavestenje(null, "NE_STIZE_KOD", Duration.ofDays(300), Duration.ofDays(250));

        ciscenje.ocisti();

        assertTrue(postoji("obavestenja", resenoPre179));
        assertFalse(postoji("obavestenja", resenoPre181));
        assertTrue(postoji("obavestenja", otvorenoStaro));
        assertFalse(postoji("obavestenja", bezStudenta));
    }

    // ---------------------------------------------------------------- pravila koja čitaju stare redove

    /** Pravilo "najviše 1 mejl na sat po (vrsta, indeks)" čita {@code MEJL_NASTAVNIKU*} brojače: oni ostaju bar sat vremena. */
    @Test
    void brojaciMejlaNastavnikuPreziveCiscenjeSatVremena() {
        long poluSata = pokusaj(VrstaPokusaja.MEJL_NASTAVNIKU, Duration.ofMinutes(30));
        long ukupnoPoluSata = pokusaj(VrstaPokusaja.MEJL_NASTAVNIKU_UKUPNO, Duration.ofMinutes(59));
        long dvaSata = pokusaj(VrstaPokusaja.MEJL_NASTAVNIKU, Duration.ofHours(2));

        ciscenje.ocisti();

        assertTrue(postoji("pokusaji", poluSata));
        assertTrue(postoji("pokusaji", ukupnoPoluSata));
        // ostalo je po roku od 2 dana: ne smeta pravilu od 1 h
        assertTrue(postoji("pokusaji", dvaSata));
    }

    /** Pravilo "najviše 1 PREVISE_KODOVA na 24 h po studentu" čita i rešena obaveštenja: čiste se tek posle 180 dana. */
    @Test
    void resenoPreviseKodovaPrezivljavaCiscenjeIPravilo24hJosVazi() {
        Student s = studenti.napravi("ana@example.test");
        long id = obavestenje(s, "PREVISE_KODOVA", Duration.ofHours(23), Duration.ofHours(22));

        ciscenje.ocisti();

        assertTrue(postoji("obavestenja", id));
        obavestenjaServis.previseKodova(s.getId(), "t9-ip");
        assertEquals(1, jdbc.queryForObject("select count(*) from obavestenja where student_id = ?", Integer.class, s.getId()),
                "drugo PREVISE_KODOVA u 24 h se ne pravi");
    }

    // ---------------------------------------------------------------- ostalo

    @Test
    void brisanjeIdeUGrupamaDoKraja() {
        ciscenje.velicinaGrupe(3);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            ids.add(pokusaj(VrstaPokusaja.KOD_SLANJE_IP, Duration.ofDays(5)));
        }
        long svez = pokusaj(VrstaPokusaja.KOD_SLANJE_IP, Duration.ofMinutes(1));

        ciscenje.ocisti();

        for (long id : ids) {
            assertFalse(postoji("pokusaji", id));
        }
        assertTrue(postoji("pokusaji", svez));
    }

    @Test
    void nistaSvezeNiStudentiSeNeDiraju() {
        Student s = studenti.napravi("ana@example.test");
        long u = uredjaj(s, Duration.ofDays(-100), null);
        long k = kod(s, Duration.ofHours(1));
        long o = obavestenje(s, "NE_STIZE_KOD", Duration.ofDays(1), null);
        long ses = sesija(Duration.ofDays(-1), null);

        ciscenje.ocisti();
        ciscenje.ocisti();

        assertTrue(postoji("studentski_uredjaji", u));
        assertTrue(postoji("kodovi_za_prijavu", k));
        assertTrue(postoji("obavestenja", o));
        assertTrue(postoji("nastavnicke_sesije", ses));
        assertTrue(studentRepository.findById(s.getId()).isPresent());
    }

    @Test
    void ocistiVracaBrojObrisanihPoTabelama() {
        Student s = studenti.napravi("ana@example.test");
        pokusaj(VrstaPokusaja.KOD_SLANJE_IP, Duration.ofDays(5));
        pokusaj(VrstaPokusaja.KOD_SLANJE_IP, Duration.ofDays(5));
        kod(s, Duration.ofDays(3));
        stanje(Duration.ofHours(5), null);
        povezivanje(Duration.ofHours(5), null);
        sesija(Duration.ofDays(40), null);
        uredjaj(s, Duration.ofDays(40), null);
        obavestenje(s, "NE_STIZE_KOD", Duration.ofDays(300), Duration.ofDays(200));

        CiscenjePrijave.Obrisano o = ciscenje.ocisti();

        // u bazi mogu biti i redovi drugih testova, pa najmanje ono što je ovaj napravio
        assertTrue(o.pokusaji() >= 2, o.toString());
        assertTrue(o.kodovi() >= 1, o.toString());
        assertTrue(o.ltiStanja() >= 1, o.toString());
        assertTrue(o.ltiPovezivanja() >= 1, o.toString());
        assertTrue(o.nastavnickeSesije() >= 1, o.toString());
        assertTrue(o.uredjaji() >= 1, o.toString());
        assertTrue(o.obavestenja() >= 1, o.toString());
    }
}
