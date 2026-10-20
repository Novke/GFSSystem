package tri.novica.gfssystem.repository.prijava;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.lti.LtiKljuc;
import tri.novica.gfssystem.entity.lti.LtiPovezivanje;
import tri.novica.gfssystem.entity.lti.LtiRegistracija;
import tri.novica.gfssystem.entity.lti.LtiStanje;
import tri.novica.gfssystem.entity.prijava.KljucAplikacije;
import tri.novica.gfssystem.entity.prijava.KodZaPrijavu;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.NastavnickaSesija;
import tri.novica.gfssystem.entity.prijava.Obavestenje;
import tri.novica.gfssystem.entity.prijava.Pokusaj;
import tri.novica.gfssystem.entity.prijava.SpoljniIdentitet;
import tri.novica.gfssystem.entity.prijava.StudentskiUredjaj;
import tri.novica.gfssystem.entity.prijava.VrstaIdentiteta;
import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.lti.LtiKljucRepository;
import tri.novica.gfssystem.repository.lti.LtiPovezivanjeRepository;
import tri.novica.gfssystem.repository.lti.LtiRegistracijaRepository;
import tri.novica.gfssystem.repository.lti.LtiStanjeRepository;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Šema prijave (Flyway V15) i repozitorijumi nad pravom MySQL bazom: svaka nova tabela čuva i učitava red, upiti koje
 * kasniji servisi koriste, {@code UNIQUE} na heševima tokena i brisanje u bazi (CASCADE na studentu i registraciji,
 * SET NULL za registraciju spoljnog identiteta). Sve u transakciji testa (rollback na kraju).
 */
@SpringBootTest
@Transactional
class PrijavaSemaIT {

    static final LocalDateTime SADA = LocalDateTime.of(2026, 10, 19, 12, 0, 0);

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired StudentRepository studentRepository;
    @Autowired NastavnickaSesijaRepository sesijaRepository;
    @Autowired StudentskiUredjajRepository uredjajRepository;
    @Autowired KodZaPrijavuRepository kodRepository;
    @Autowired SpoljniIdentitetRepository identitetRepository;
    @Autowired ObavestenjeRepository obavestenjeRepository;
    @Autowired PokusajRepository pokusajRepository;
    @Autowired KljucAplikacijeRepository kljucAplikacijeRepository;
    @Autowired LtiRegistracijaRepository registracijaRepository;
    @Autowired LtiKljucRepository ltiKljucRepository;
    @Autowired LtiStanjeRepository stanjeRepository;
    @Autowired LtiPovezivanjeRepository povezivanjeRepository;

    Grupa grupa;
    Student student;

    @BeforeEach
    void seed() {
        grupa = new Grupa();
        grupa.setNaziv("PR-A");
        grupa.setGodinaUpisa(2025);
        em.persist(grupa);
        student = student("PRX1", 2025);
    }

    private Student student(String indeks, int godina) {
        Student s = new Student();
        s.setIme("Ime");
        s.setPrezime("Prezime");
        s.setIndeks(indeks);
        s.setGodina(godina);
        s.setEmail("student@example.test");
        s.setGrupa(grupa);
        em.persist(s);
        em.flush();
        return s;
    }

    private static String hes() {
        return TokenHash.od(UUID.randomUUID().toString());
    }

    private NastavnickaSesija sesija(String tokenHes, LocalDateTime poslednjiUlaz, LocalDateTime istice,
                                     LocalDateTime opozvana) {
        NastavnickaSesija s = new NastavnickaSesija();
        s.setTokenHes(tokenHes);
        s.setOpis("Linux · Firefox");
        s.setIp("203.0.113.7");
        s.setKreirano(SADA.minusDays(1));
        s.setPoslednjiUlaz(poslednjiUlaz);
        s.setIstice(istice);
        s.setOpozvana(opozvana);
        return sesijaRepository.saveAndFlush(s);
    }

    private StudentskiUredjaj uredjaj(Student s, String tokenHes, LocalDateTime istice) {
        StudentskiUredjaj u = new StudentskiUredjaj();
        u.setStudent(s);
        u.setTokenHes(tokenHes);
        u.setOpis("Android · Chrome");
        u.setNacin(NacinPrijave.KOD);
        u.setPrviUlaz(SADA.minusHours(2));
        u.setPoslednjiUlaz(SADA.minusHours(1));
        u.setIstice(istice);
        return uredjajRepository.saveAndFlush(u);
    }

    private KodZaPrijavu kod(Student s, LocalDateTime kreirano, LocalDateTime istice) {
        KodZaPrijavu k = new KodZaPrijavu();
        k.setStudent(s);
        k.setKodHes(hes());
        k.setSo("0123456789abcdef0123456789abcdef");
        k.setKreirano(kreirano);
        k.setIstice(istice);
        return kodRepository.saveAndFlush(k);
    }

    private LtiRegistracija registracija(String clientId) {
        LtiRegistracija r = new LtiRegistracija();
        r.setNaziv("Moodle test");
        r.setIzdavac("https://moodle.example.test");
        r.setClientId(clientId);
        r.setDeploymentId("1");
        r.setAuthUrl("https://moodle.example.test/mod/lti/auth.php");
        r.setJwksUrl("https://moodle.example.test/mod/lti/certs.php");
        r.setAktivna(true);
        r.setKreirano(SADA);
        return registracijaRepository.saveAndFlush(r);
    }

    private SpoljniIdentitet identitet(Student s, String subjekat, LtiRegistracija r) {
        SpoljniIdentitet i = new SpoljniIdentitet();
        i.setStudent(s);
        i.setVrsta(VrstaIdentiteta.LTI);
        i.setIzdavac("https://moodle.example.test");
        i.setSubjekat(subjekat);
        i.setRegistracija(r);
        i.setKreirano(SADA);
        i.setPoslednjiUlaz(SADA);
        return identitetRepository.saveAndFlush(i);
    }

    private Obavestenje obavestenje(Student s, LocalDateTime kreirano, LocalDateTime procitano, LocalDateTime reseno) {
        Obavestenje o = new Obavestenje();
        o.setVrsta(VrstaObavestenja.NE_STIZE_KOD);
        o.setStudent(s);
        o.setPodaci("{\"indeks\":\"PRX1\"}");
        o.setKreirano(kreirano);
        o.setProcitano(procitano);
        o.setReseno(reseno);
        return obavestenjeRepository.saveAndFlush(o);
    }

    private LtiStanje stanje(LtiRegistracija r, String stanjeHes) {
        LtiStanje s = new LtiStanje();
        s.setStanjeHes(stanjeHes);
        s.setNonce("nonce-" + UUID.randomUUID());
        s.setKolacicHes(hes());
        s.setRegistracija(r);
        s.setKreirano(SADA);
        s.setIstice(SADA.plusMinutes(10));
        return stanjeRepository.saveAndFlush(s);
    }

    private LtiPovezivanje povezivanje(LtiRegistracija r, String tokenHes) {
        LtiPovezivanje p = new LtiPovezivanje();
        p.setTokenHes(tokenHes);
        p.setRegistracija(r);
        p.setSubjekat("sub-1");
        p.setIme("Ime Prezime");
        p.setIstice(SADA.plusMinutes(30));
        return povezivanjeRepository.saveAndFlush(p);
    }

    // ------------------------------------------------------------------ šema

    @Test
    void koloneImajuTipoveIzSpecifikacije() {
        Map<String, String> ocekivano = Map.ofEntries(
                Map.entry("nastavnicke_sesije.token_hes", "char(64)"),
                Map.entry("nastavnicke_sesije.opis", "varchar(120)"),
                Map.entry("nastavnicke_sesije.ip", "varchar(64)"),
                Map.entry("nastavnicke_sesije.istice", "datetime(3)"),
                Map.entry("studentski_uredjaji.nacin", "varchar(30)"),
                Map.entry("studentski_uredjaji.token_hes", "char(64)"),
                Map.entry("kodovi_za_prijavu.kod_hes", "char(64)"),
                Map.entry("kodovi_za_prijavu.so", "char(32)"),
                Map.entry("kodovi_za_prijavu.pogresni", "int"),
                Map.entry("spoljni_identiteti.vrsta", "varchar(30)"),
                Map.entry("spoljni_identiteti.izdavac", "varchar(255)"),
                Map.entry("spoljni_identiteti.subjekat", "varchar(255)"),
                Map.entry("obavestenja.vrsta", "varchar(40)"),
                Map.entry("obavestenja.podaci", "text"),
                Map.entry("pokusaji.vrsta", "varchar(30)"),
                Map.entry("pokusaji.kljuc", "varchar(128)"),
                Map.entry("pokusaji.vreme", "datetime(3)"),
                Map.entry("kljucevi_aplikacije.naziv", "varchar(40)"),
                Map.entry("kljucevi_aplikacije.vrednost", "varchar(100)"),
                Map.entry("lti_kljucevi.privatni", "text"),
                Map.entry("lti_kljucevi.javni", "text"),
                Map.entry("lti_stanja.stanje_hes", "char(64)"),
                Map.entry("lti_stanja.nonce", "varchar(64)"),
                Map.entry("lti_stanja.kolacic_hes", "char(64)"),
                Map.entry("lti_povezivanja.token_hes", "char(64)"),
                Map.entry("lti_povezivanja.ime", "varchar(200)"));
        ocekivano.forEach((kolona, tip) -> {
            String[] d = kolona.split("\\.");
            List<String> tipovi = jdbc.queryForList("""
                    select COLUMN_TYPE from information_schema.COLUMNS
                    where TABLE_SCHEMA = database() and TABLE_NAME = ? and COLUMN_NAME = ?""",
                    String.class, d[0], d[1]);
            assertEquals(List.of(tip), tipovi, kolona);
        });
    }

    // ------------------------------------------------------------------ nastavničke sesije

    @Test
    void sesijaNastavnikaSeCuvaIUcitavaPoHesu() {
        String h = hes();
        NastavnickaSesija s = sesija(h, SADA, SADA.plusDays(90), null);
        em.clear();

        NastavnickaSesija ucitana = sesijaRepository.findByTokenHes(h).orElseThrow();
        assertEquals(s.getId(), ucitana.getId());
        assertEquals("Linux · Firefox", ucitana.getOpis());
        assertEquals("203.0.113.7", ucitana.getIp());
        assertEquals(SADA.plusDays(90), ucitana.getIstice());
        assertNull(ucitana.getOpozvana());
        assertTrue(sesijaRepository.findByTokenHes(hes()).isEmpty());
    }

    @Test
    void aktivneSesijeBezOpozvanihIIsteklihPoPoslednjemUlazu() {
        NastavnickaSesija starija = sesija(hes(), SADA.minusHours(5), SADA.plusDays(1), null);
        NastavnickaSesija novija = sesija(hes(), SADA.minusHours(1), SADA.plusDays(1), null);
        NastavnickaSesija opozvana = sesija(hes(), SADA, SADA.plusDays(1), SADA.minusMinutes(1));
        NastavnickaSesija istekla = sesija(hes(), SADA, SADA.minusSeconds(1), null);
        List<Long> moje = List.of(starija.getId(), novija.getId(), opozvana.getId(), istekla.getId());

        // baza može imati commit-ovane sesije drugih testova: gledaju se samo ove četiri
        List<Long> ids = sesijaRepository.findAllByOpozvanaIsNullAndIsticeAfterOrderByPoslednjiUlazDesc(SADA)
                .stream().map(NastavnickaSesija::getId).filter(moje::contains).toList();
        assertEquals(List.of(novija.getId(), starija.getId()), ids);
    }

    @Test
    void hesSesijeJeJedinstven() {
        String h = hes();
        sesija(h, SADA, SADA.plusDays(1), null);
        assertThrows(DataIntegrityViolationException.class, () -> sesija(h, SADA, SADA.plusDays(1), null));
    }

    // ------------------------------------------------------------------ studentski uređaji

    @Test
    void uredjajSeCuvaIUcitavaPoHesu() {
        String h = hes();
        StudentskiUredjaj u = uredjaj(student, h, SADA.plusDays(365));
        em.clear();

        StudentskiUredjaj ucitan = uredjajRepository.findByTokenHes(h).orElseThrow();
        assertEquals(u.getId(), ucitan.getId());
        assertEquals(student.getId(), ucitan.getStudent().getId());
        assertEquals(NacinPrijave.KOD, ucitan.getNacin());
        assertEquals("Android · Chrome", ucitan.getOpis());
        assertNull(ucitan.getOpozvan());
    }

    @Test
    void aktivniUredjajiStudentaIOpozivanjeSvih() {
        Student drugi = student("PRX2", 2025);
        StudentskiUredjaj a = uredjaj(student, hes(), SADA.plusDays(10));
        StudentskiUredjaj b = uredjaj(student, hes(), SADA.plusDays(20));
        uredjaj(student, hes(), SADA.minusDays(1));   // istekao
        StudentskiUredjaj tudji = uredjaj(drugi, hes(), SADA.plusDays(10));

        assertEquals(List.of(a.getId(), b.getId()).stream().sorted().toList(),
                uredjajRepository.findAllByStudentIdAndOpozvanIsNullAndIsticeAfter(student.getId(), SADA).stream()
                        .map(StudentskiUredjaj::getId).sorted().toList());

        int opozvano = uredjajRepository.opozoviSve(student.getId(), SADA);
        assertEquals(3, opozvano);   // i istekli dobija vreme opoziva; već opozvani se ne diraju
        em.clear();
        assertTrue(uredjajRepository.findAllByStudentIdAndOpozvanIsNullAndIsticeAfter(student.getId(), SADA).isEmpty());
        assertEquals(SADA, uredjajRepository.findById(a.getId()).orElseThrow().getOpozvan());
        assertNull(uredjajRepository.findById(tudji.getId()).orElseThrow().getOpozvan());
        assertEquals(0, uredjajRepository.opozoviSve(student.getId(), SADA.plusHours(1)));
    }

    @Test
    void hesUredjajaJeJedinstven() {
        String h = hes();
        uredjaj(student, h, SADA.plusDays(1));
        assertThrows(DataIntegrityViolationException.class, () -> uredjaj(student, h, SADA.plusDays(1)));
    }

    // ------------------------------------------------------------------ kodovi

    @Test
    void aktivniKodoviSuNeiskorisceniNeponisteniINeistekli() {
        Student drugi = student("PRX3", 2025);
        KodZaPrijavu aktivan = kod(student, SADA.minusMinutes(2), SADA.plusMinutes(8));
        KodZaPrijavu iskoriscen = kod(student, SADA.minusMinutes(3), SADA.plusMinutes(7));
        iskoriscen.setIskoriscen(SADA.minusMinutes(1));
        KodZaPrijavu ponisten = kod(student, SADA.minusMinutes(4), SADA.plusMinutes(6));
        ponisten.setPonisten(SADA.minusMinutes(1));
        kod(student, SADA.minusMinutes(20), SADA.minusMinutes(10));   // istekao
        kod(drugi, SADA.minusMinutes(1), SADA.plusMinutes(9));         // drugi student
        em.flush();
        em.clear();

        List<KodZaPrijavu> aktivni = kodRepository.findAktivne(student.getId(), SADA);
        assertEquals(List.of(aktivan.getId()), aktivni.stream().map(KodZaPrijavu::getId).toList());
        KodZaPrijavu k = aktivni.getFirst();
        assertEquals(0, k.getPogresni());
        assertEquals(64, k.getKodHes().length());
        assertEquals("0123456789abcdef0123456789abcdef", k.getSo());

        assertEquals(3, kodRepository.countByStudentIdAndKreiranoAfter(student.getId(), SADA.minusMinutes(5)));
        assertEquals(4, kodRepository.countByStudentIdAndKreiranoAfter(student.getId(), SADA.minusHours(1)));
    }

    // ------------------------------------------------------------------ spoljni identiteti

    @Test
    void spoljniIdentitetPoTrojciIPoStudentu() {
        LtiRegistracija r = registracija("client-" + UUID.randomUUID());
        SpoljniIdentitet i = identitet(student, "sub-42", r);
        em.clear();

        SpoljniIdentitet ucitan = identitetRepository
                .findByVrstaAndIzdavacAndSubjekat(VrstaIdentiteta.LTI, "https://moodle.example.test", "sub-42")
                .orElseThrow();
        assertEquals(i.getId(), ucitan.getId());
        assertEquals(student.getId(), ucitan.getStudent().getId());
        assertEquals(r.getId(), ucitan.getRegistracija().getId());
        assertEquals(List.of(i.getId()),
                identitetRepository.findAllByStudentId(student.getId()).stream().map(SpoljniIdentitet::getId).toList());
        assertTrue(identitetRepository
                .findByVrstaAndIzdavacAndSubjekat(VrstaIdentiteta.LTI, "https://moodle.example.test", "sub-43")
                .isEmpty());
    }

    @Test
    void brisanjeRegistracijeOstavljaIdentitetBezRegistracije() {
        LtiRegistracija r = registracija("client-" + UUID.randomUUID());
        SpoljniIdentitet i = identitet(student, "sub-50", r);
        em.flush();

        jdbc.update("delete from lti_registracije where id = ?", r.getId());
        assertNull(jdbc.queryForObject("select registracija_id from spoljni_identiteti where id = ?", Long.class,
                i.getId()));
    }

    @Test
    void trojkaIdentitetaJeJedinstvena() {
        Student drugi = student("PRX4", 2025);
        identitet(student, "sub-60", null);
        assertThrows(DataIntegrityViolationException.class, () -> identitet(drugi, "sub-60", null));
    }

    // ------------------------------------------------------------------ obaveštenja

    @Test
    void obavestenjaOtvorenaINeprocitana() {
        long otvorenaPre = obavestenjeRepository.countByResenoIsNull();
        long neprocitanaPre = obavestenjeRepository.countByProcitanoIsNull();

        Obavestenje bezStudenta = obavestenje(null, SADA.minusHours(1), null, null);
        Obavestenje procitano = obavestenje(student, SADA.minusHours(2), SADA.minusMinutes(5), null);
        obavestenje(student, SADA.minusHours(3), SADA.minusHours(2), SADA.minusHours(1));   // rešeno
        Obavestenje previse = new Obavestenje();
        previse.setVrsta(VrstaObavestenja.PREVISE_KODOVA);
        previse.setStudent(student);
        previse.setPodaci("{}");
        previse.setKreirano(SADA);
        obavestenjeRepository.saveAndFlush(previse);
        em.clear();

        assertEquals(otvorenaPre + 3, obavestenjeRepository.countByResenoIsNull());
        assertEquals(neprocitanaPre + 2, obavestenjeRepository.countByProcitanoIsNull());

        Page<Obavestenje> strana = obavestenjeRepository.findAllByResenoIsNull(
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "kreirano")));
        List<Long> ids = strana.getContent().stream().map(Obavestenje::getId).toList();
        assertTrue(ids.containsAll(List.of(bezStudenta.getId(), procitano.getId(), previse.getId())), ids.toString());
        Obavestenje ucitano = obavestenjeRepository.findById(bezStudenta.getId()).orElseThrow();
        assertNull(ucitano.getStudent());
        assertEquals(VrstaObavestenja.NE_STIZE_KOD, ucitano.getVrsta());
        assertEquals("{\"indeks\":\"PRX1\"}", ucitano.getPodaci());
        assertEquals(VrstaObavestenja.PREVISE_KODOVA,
                obavestenjeRepository.findById(previse.getId()).orElseThrow().getVrsta());
    }

    // ------------------------------------------------------------------ pokušaji

    @Test
    void pokusajiSeBrojePoVrstiIKljucuIBrisuStariji() {
        String ip = "ip-" + UUID.randomUUID();
        for (int m : new int[]{1, 10, 50, 70}) {
            Pokusaj p = new Pokusaj();
            p.setVrsta(VrstaPokusaja.NASTAVNIK_GRESKA_IP);
            p.setKljuc(ip);
            p.setVreme(SADA.minusMinutes(m));
            pokusajRepository.save(p);
        }
        Pokusaj drugaVrsta = new Pokusaj();
        drugaVrsta.setVrsta(VrstaPokusaja.KOD_GRESKA_IP);
        drugaVrsta.setKljuc(ip);
        drugaVrsta.setVreme(SADA);
        pokusajRepository.saveAndFlush(drugaVrsta);

        assertEquals(2, pokusajRepository.countByVrstaAndKljucAndVremeAfter(
                VrstaPokusaja.NASTAVNIK_GRESKA_IP, ip, SADA.minusMinutes(15)));
        assertEquals(3, pokusajRepository.countByVrstaAndKljucAndVremeAfter(
                VrstaPokusaja.NASTAVNIK_GRESKA_IP, ip, SADA.minusHours(1)));
        assertEquals(1, pokusajRepository.countByVrstaAndKljucAndVremeAfter(
                VrstaPokusaja.KOD_GRESKA_IP, ip, SADA.minusHours(1)));

        assertTrue(pokusajRepository.obrisiStarije(SADA.minusMinutes(30)) >= 2);
        em.clear();
        assertEquals(2, pokusajRepository.countByVrstaAndKljucAndVremeAfter(
                VrstaPokusaja.NASTAVNIK_GRESKA_IP, ip, SADA.minusDays(1)));
    }

    @Test
    void vrstePokusajaSuIzPlana() {
        assertEquals(List.of("NASTAVNIK_GRESKA_IP", "KOD_SLANJE_IP", "KOD_SLANJE_STUDENT", "KOD_GRESKA_IP", "POMOC_IP",
                        "POMOC_INDEKS", "KOD_SA_KARTICE", "MEJL_NASTAVNIKU", "MEJL_NASTAVNIKU_UKUPNO"),
                java.util.Arrays.stream(VrstaPokusaja.values()).map(Enum::name).toList());
    }

    // ------------------------------------------------------------------ ključevi aplikacije

    @Test
    void kljucAplikacijePoNazivuIJedinstvenNaziv() {
        String naziv = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        KljucAplikacije k = new KljucAplikacije();
        k.setNaziv(naziv);
        k.setVrednost("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        k.setKreirano(SADA);
        kljucAplikacijeRepository.saveAndFlush(k);
        em.clear();

        assertEquals(k.getId(), kljucAplikacijeRepository.findByNaziv(naziv).orElseThrow().getId());
        assertTrue(kljucAplikacijeRepository.findByNaziv(naziv + "X").isEmpty());

        KljucAplikacije dupli = new KljucAplikacije();
        dupli.setNaziv(naziv);
        dupli.setVrednost("BBBB");
        dupli.setKreirano(SADA);
        assertThrows(DataIntegrityViolationException.class, () -> kljucAplikacijeRepository.saveAndFlush(dupli));
    }

    // ------------------------------------------------------------------ LTI

    @Test
    void ltiRegistracijaKljucStanjeIPovezivanjeSeCuvajuIUcitavaju() {
        String clientId = "client-" + UUID.randomUUID();
        LtiRegistracija r = registracija(clientId);
        LtiKljuc kljuc = new LtiKljuc();
        kljuc.setKid("kid-" + UUID.randomUUID());
        kljuc.setPrivatni("privatni-pkcs8-base64");
        kljuc.setJavni("{\"kty\":\"RSA\"}");
        kljuc.setKreirano(SADA);
        kljuc.setAktivan(true);
        ltiKljucRepository.saveAndFlush(kljuc);
        String stanjeHes = hes();
        LtiStanje st = stanje(r, stanjeHes);
        String parHes = hes();
        LtiPovezivanje p = povezivanje(r, parHes);
        em.clear();

        LtiRegistracija ucitana = registracijaRepository
                .findByIzdavacAndClientIdAndDeploymentId("https://moodle.example.test", clientId, "1").orElseThrow();
        assertEquals(r.getId(), ucitana.getId());
        assertTrue(ucitana.isAktivna());
        assertEquals("https://moodle.example.test/mod/lti/certs.php", ucitana.getJwksUrl());

        LtiKljuc k = ltiKljucRepository.findByKid(kljuc.getKid()).orElseThrow();
        assertTrue(k.isAktivan());
        assertTrue(ltiKljucRepository.findAllByAktivanTrueOrderByKreiranoDescIdDesc().stream()
                .anyMatch(x -> x.getId().equals(kljuc.getId())));

        LtiStanje s = stanjeRepository.findByStanjeHes(stanjeHes).orElseThrow();
        assertEquals(st.getId(), s.getId());
        assertEquals(r.getId(), s.getRegistracija().getId());
        assertNull(s.getIskorisceno());

        LtiPovezivanje pp = povezivanjeRepository.findByTokenHes(parHes).orElseThrow();
        assertEquals(p.getId(), pp.getId());
        assertEquals("sub-1", pp.getSubjekat());
        assertEquals("Ime Prezime", pp.getIme());
    }

    @Test
    void trojkaRegistracijeJeJedinstvena() {
        String clientId = "client-" + UUID.randomUUID();
        registracija(clientId);
        assertThrows(DataIntegrityViolationException.class, () -> registracija(clientId));
    }

    @Test
    void hesLtiStanjaJeJedinstven() {
        LtiRegistracija r = registracija("client-" + UUID.randomUUID());
        String h = hes();
        stanje(r, h);
        assertThrows(DataIntegrityViolationException.class, () -> stanje(r, h));
    }

    @Test
    void hesLtiPovezivanjaJeJedinstven() {
        LtiRegistracija r = registracija("client-" + UUID.randomUUID());
        String h = hes();
        povezivanje(r, h);
        assertThrows(DataIntegrityViolationException.class, () -> povezivanje(r, h));
    }

    @Test
    void brisanjeRegistracijeBriseStanjaIPovezivanja() {
        LtiRegistracija r = registracija("client-" + UUID.randomUUID());
        LtiStanje s = stanje(r, hes());
        LtiPovezivanje p = povezivanje(r, hes());
        em.flush();

        jdbc.update("delete from lti_registracije where id = ?", r.getId());
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_stanja where id = ?", Integer.class, s.getId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from lti_povezivanja where id = ?", Integer.class,
                p.getId()));
    }

    // ------------------------------------------------------------------ brisanje studenta

    @Test
    void brisanjeStudentaBriseUredjajeKodoveIdentiteteIObavestenja() {
        StudentskiUredjaj u = uredjaj(student, hes(), SADA.plusDays(1));
        KodZaPrijavu k = kod(student, SADA, SADA.plusMinutes(10));
        SpoljniIdentitet i = identitet(student, "sub-70", null);
        Obavestenje o = obavestenje(student, SADA, null, null);
        Obavestenje bezStudenta = obavestenje(null, SADA, null, null);
        em.flush();

        jdbc.update("delete from studenti where id = ?", student.getId());

        assertEquals(0, broj("studentski_uredjaji", u.getId()));
        assertEquals(0, broj("kodovi_za_prijavu", k.getId()));
        assertEquals(0, broj("spoljni_identiteti", i.getId()));
        assertEquals(0, broj("obavestenja", o.getId()));
        assertEquals(1, broj("obavestenja", bezStudenta.getId()));
    }

    private int broj(String tabela, Long id) {
        return jdbc.queryForObject("select count(*) from " + tabela + " where id = ?", Integer.class, id);
    }

    // ------------------------------------------------------------------ student: zaključavanje i indeks

    @Test
    void studentSeZakljucavaPoId() {
        em.clear();
        Student zakljucan = studentRepository.findByIdForUpdate(student.getId()).orElseThrow();
        assertEquals(student.getId(), zakljucan.getId());
        assertTrue(studentRepository.findByIdForUpdate(-1L).isEmpty());
    }

    @Test
    void poNormalizovanomIndeksuNajnovijiUpisPrvi() {
        Student stariji = student("prx 9", 2023);
        Student noviji = student("PRX9", 2025);
        Student srednji = student("Prx9", 2024);
        em.clear();

        assertEquals(List.of(noviji.getId(), srednji.getId(), stariji.getId()),
                studentRepository.findAllPoNormalizovanomIndeksu("PRX9").stream().map(Student::getId).toList());
        assertEquals(noviji.getId(), studentRepository.findNajnovijiPoIndeksu("PRX9").orElseThrow().getId());
        assertTrue(studentRepository.findNajnovijiPoIndeksu("PRX0").isEmpty());
    }
}
