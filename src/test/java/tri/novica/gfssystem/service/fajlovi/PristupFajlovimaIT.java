package tri.novica.gfssystem.service.fajlovi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.PokreniCmd;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.entity.fajlovi.KodFajlova;
import tri.novica.gfssystem.repository.fajlovi.FajlPrezentacijeRepository;
import tri.novica.gfssystem.repository.fajlovi.SlajdoviIzvodjenjaRepository;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pravila "ko sme da preuzme" (spec fajlova, odeljak 4): spisak koda i nalog studenta, nad pravom bazom. */
@SpringBootTest
@Import(PomerljivSatConfig.class)
class PristupFajlovimaIT {

    @Autowired PristupFajlovima pristup;
    @Autowired KodoviFajlova kodovi;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired IzvodjenjeRepository izvodjenjeRepository;
    @Autowired SlajdoviIzvodjenjaRepository prikazi;
    @Autowired FajlPrezentacijeRepository fajloviRepo;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired MedijService medijService;
    @Autowired PomerljivSatConfig.Sat sat;
    @Autowired JdbcTemplate jdbc;

    CasPodaci d;
    FajloviPodaci p;
    Long g1, g2, g0;
    Long s1, s2, s3;
    Long l1, l2;
    Long prez, prez2, slajd1, slajd2, fBez, f1, f2, fDrugo;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        p = new FajloviPodaci(jdbc, medijService, prezentacijaService, d.predmetId);
        g0 = d.grupa(2025);
        g1 = d.grupa(2026);
        g2 = d.grupa(2026);
        s1 = d.student(g1, "Prvi");
        s2 = d.student(g2, "Drugi");     // grupa bez predavanja iz ovog predmeta: predmet ne sluša
        s3 = d.student(g0, "Ponavlja");
        l1 = d.predavanje(g1, LocalDate.now());
        l2 = d.predavanje(g1, LocalDate.now());
        d.aktivnost(l1, s3, "PRISUSTVO");   // dodatna grupa g1 za ovaj predmet

        prez = p.prezentacija("Vežbe A");
        slajd1 = p.slajd(prez, "Prikazan");
        slajd2 = p.slajd(prez, "Preskočen");
        fBez = p.fajl(prez, null, "polazni.docx");
        f1 = p.fajl(prez, slajd1, "uz-slajd-1.xlsx");
        f2 = p.fajl(prez, slajd2, "uz-slajd-2.docx");
        prez2 = p.prezentacija("Vežbe B");
        fDrugo = p.fajl(prez2, null, "drugo.docx");
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
        p.ocisti();
    }

    Long pokreni(Long prezId, Long predavanjeId) {
        IzvodjenjeInfo i = izvodjenjeService.pokreni(prezId, new PokreniCmd(false, null, predavanjeId));
        return i.id();
    }

    KodFajlova kod(Long izvodjenjeId) {
        return kodovi.obezbedi(izvodjenjeRepository.findSaVezama(izvodjenjeId).orElseThrow()).orElseThrow();
    }

    List<Long> ids(List<FajlPrezentacije> l) {
        return l.stream().map(FajlPrezentacije::getId).toList();
    }

    void prikazi(Long izvodjenjeId, Long slajdId) {
        prikazi.upisiPrikaz(izvodjenjeId, slajdId, LocalDateTime.now());
    }

    // ---------------------------------------------------------------- čas

    @Test
    void naPocetkuSamoFajlBezSlajdaPaSlajdKadJePrikazan() {
        Long iz = pokreni(prez, l1);
        KodFajlova k = kod(iz);
        assertEquals(List.of(fBez), ids(pristup.fajloviKoda(k)));
        assertTrue(pristup.naVazecemKodu(fBez));
        assertFalse(pristup.naVazecemKodu(f1));

        prikazi(iz, slajd1);
        assertEquals(List.of(fBez, f1), ids(pristup.fajloviKoda(k)));   // redom rb
        assertTrue(pristup.naVazecemKodu(f1));
        assertFalse(pristup.naVazecemKodu(f2));   // slajd nikad prikazan
    }

    @Test
    void ponovniPrikazIstogSlajdaNePravidupliranje() {
        Long iz = pokreni(prez, l1);
        prikazi(iz, slajd1);
        prikazi(iz, slajd1);
        assertEquals(1, prikazi.broj(iz, slajd1));
    }

    @Test
    void fajlOkacenNaVecPrikazanSlajdOdmahJeNaSpisku() {
        Long iz = pokreni(prez, l1);
        prikazi(iz, slajd1);
        Long kasniji = p.fajl(prez, slajd1, "kasnije.docx");
        assertEquals(List.of(fBez, f1, kasniji), ids(pristup.fajloviKoda(kod(iz))));
    }

    @Test
    void drugoPredavanjeNeVidiFajloveTudjihPrezentacija() {
        Long iz1 = pokreni(prez, l1);
        Long iz2 = pokreni(prez2, l2);
        KodFajlova k1 = kod(iz1);
        KodFajlova k2 = kod(iz2);
        assertNotEquals(k1.getId(), k2.getId());
        assertEquals(List.of(fBez), ids(pristup.fajloviKoda(k1)));
        assertEquals(List.of(fDrugo), ids(pristup.fajloviKoda(k2)));
    }

    @Test
    void fajloviSvihPrezentacijaPredavanjaIdeRedomPrvogPocetka() {
        Long iz1 = pokreni(prez, l1);
        KodFajlova k = kod(iz1);
        izvodjenjeService.pokreni(prez2, new PokreniCmd(false, null, l1));
        assertEquals(List.of(fBez, fDrugo), ids(pristup.fajloviKoda(k)));
    }

    @Test
    void izvodjenjeBezPredavanjaVidiSvojePrikazaneSlajdove() {
        Long iz = pokreni(prez, null);
        KodFajlova k = kod(iz);
        assertEquals(List.of(fBez), ids(pristup.fajloviKoda(k)));
        prikazi(iz, slajd2);
        assertEquals(List.of(fBez, f2), ids(pristup.fajloviKoda(k)));
        assertTrue(pristup.naVazecemKodu(f2));
        p.zavrsiIzvodjenje(iz);
        assertFalse(pristup.naVazecemKodu(fBez));   // izvođenje nije AKTIVNO
    }

    @Test
    void zavrsenoPredavanjeIIstekaoKodNisuNaVazecemKodu() {
        Long iz = pokreni(prez, l1);
        kod(iz);
        assertTrue(pristup.naVazecemKodu(fBez));
        sat.pomeri(Duration.ofHours(5).plusMinutes(1));
        assertFalse(pristup.naVazecemKodu(fBez));
    }

    @Test
    void zavrsenoPredavanjeNijeNaVazecemKodu() {
        kod(pokreni(prez, l1));
        d.zavrsi(l1);
        assertFalse(pristup.naVazecemKodu(fBez));
    }

    // ---------------------------------------------------------------- nalog

    @Test
    void izvodjenjeUTokuNeOtvaraNalog() {
        pokreni(prez, l1);
        FajlPrezentacije f = fajloviRepo.findById(fBez).orElseThrow();
        assertFalse(pristup.smeNalog(s1, f));
        assertTrue(pristup.zaNalog(s1, d.predmetId).isEmpty());
        assertTrue(pristup.zaNalogPrezentacije(s1, prez).isEmpty());
    }

    @Test
    void posleZavrsetkaStudentSopstveneGrupeVidiSveFajlovePrezentacije() {
        p.zavrsiIzvodjenje(pokreni(prez, l1));
        FajlPrezentacije f = fajloviRepo.findById(f2).orElseThrow();   // slajd nikad prikazan, ipak vidljiv
        assertTrue(pristup.smeNalog(s1, f));
        assertEquals(List.of(fBez, f1, f2), ids(pristup.zaNalogPrezentacije(s1, prez)));
        assertEquals(List.of(fBez, f1, f2), ids(pristup.zaNalog(s1, d.predmetId)));
        assertEquals(List.of(prez), List.copyOf(pristup.odrzaneZaNalog(s1, d.predmetId).keySet()));
        assertTrue(pristup.zaNalogPrezentacije(s1, prez2).isEmpty());   // prez2 nije održana
    }

    @Test
    void zaNalogIdeOdNajnovijeOdrzane() throws Exception {
        p.zavrsiIzvodjenje(pokreni(prez, l1));
        Thread.sleep(20);
        p.zavrsiIzvodjenje(pokreni(prez2, l2));
        jdbc.update("update izvodjenja set pocetak = ? where prezentacija_id = ?", LocalDateTime.now().minusDays(1), prez);
        assertEquals(List.of(fDrugo, fBez, f1, f2), ids(pristup.zaNalog(s1, d.predmetId)));
        assertEquals(List.of(prez2, prez), List.copyOf(pristup.odrzaneZaNalog(s1, d.predmetId).keySet()));
    }

    @Test
    void studentTudjeGrupeNeVidiNista() {
        p.zavrsiIzvodjenje(pokreni(prez, l1));
        FajlPrezentacije f = fajloviRepo.findById(fBez).orElseThrow();
        assertFalse(pristup.smeNalog(s2, f));
        assertTrue(pristup.zaNalog(s2, d.predmetId).isEmpty());
        assertTrue(pristup.zaNalogPrezentacije(s2, prez).isEmpty());
        assertTrue(pristup.odrzaneZaNalog(s2, d.predmetId).isEmpty());
    }

    @Test
    void studentSaDodatnomGrupomPoPredmetuVidiFajlove() {
        p.zavrsiIzvodjenje(pokreni(prez, l1));
        FajlPrezentacije f = fajloviRepo.findById(f1).orElseThrow();
        assertTrue(pristup.smeNalog(s3, f));
        assertEquals(List.of(fBez, f1, f2), ids(pristup.zaNalog(s3, d.predmetId)));
    }

    @Test
    void izvodjenjeBezGrupeNeOtvaraNalog() {
        p.zavrsiIzvodjenje(pokreni(prez, null));
        assertTrue(pristup.zaNalog(s1, d.predmetId).isEmpty());
    }

    @Test
    void nepoznatPredmetIPrezentacijaDajuPrazno() {
        assertTrue(pristup.zaNalog(s1, -1L).isEmpty());
        assertTrue(pristup.zaNalog(s1, null).isEmpty());
        assertTrue(pristup.zaNalogPrezentacije(s1, -1L).isEmpty());
        assertTrue(pristup.zaNalogPrezentacije(s1, null).isEmpty());
    }
}
