package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.prijava.SesijaInfo;
import tri.novica.gfssystem.entity.prijava.NastavnickaSesija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.prijava.NastavnickaSesijaRepository;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link NastavnikSesijaServis} nad pravom MySQL bazom (test svojstva: korisnik {@code nastavnik}, lozinka
 * {@code test-lozinka-123}), bez {@code @Transactional}: limit po IP-u upisuje u {@code REQUIRES_NEW}. Svaki test ima
 * svoj IP (sesije i brojač se čiste po njemu).
 */
@SpringBootTest
@Import(PrijavaSatConfig.class)
class NastavnikSesijaServisIT {

    static final String LOZINKA = "test-lozinka-123";
    static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

    @Autowired NastavnikSesijaServis servis;
    @Autowired NastavnickaSesijaRepository repo;
    @Autowired PomerljivSat sat;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    final List<String> ipovi = new ArrayList<>();
    String ip;

    @BeforeEach
    void setUp() {
        sat.postavi(Instant.now());
        ip = noviIp();
    }

    @AfterEach
    void ocisti() {
        for (String i : ipovi) {
            jdbc.update("delete from nastavnicke_sesije where ip = ?", i);
            jdbc.update("delete from pokusaji where kljuc = ?", i);
        }
    }

    String noviIp() {
        String i = "t" + UUID.randomUUID().toString().substring(0, 8);
        ipovi.add(i);
        return i;
    }

    LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }

    SystemException pokusajPrijave(String korisnik, String lozinka, String ip) {
        return assertThrows(SystemException.class, () -> servis.prijavi(korisnik, lozinka, ip, UA));
    }

    @Test
    void tacnaPrijavaVracaTokenIUpisujeHes() {
        String token = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        assertEquals(32, token.length());
        NastavnickaSesija s = repo.findByTokenHes(TokenHash.od(token)).orElseThrow();
        assertEquals(ip, s.getIp());
        assertEquals("Android · Chrome", s.getOpis());
        assertNull(s.getOpozvana());
        assertEquals(s.getKreirano(), s.getPoslednjiUlaz());
        assertEquals(s.getKreirano().plusDays(90), s.getIstice());
        // u bazi nema sirovog tokena
        Integer sirov = jdbc.queryForObject("select count(*) from nastavnicke_sesije where token_hes = ?", Integer.class, token);
        assertEquals(0, sirov);
    }

    @Test
    void pogresnoImeIPogresnaLozinkaDajuIstiIzuzetak() {
        SystemException a = pokusajPrijave("neko-drugi", LOZINKA, ip);
        SystemException b = pokusajPrijave("nastavnik", "pogresna", ip);
        assertEquals(401, a.getCode());
        assertEquals(401, b.getCode());
        assertEquals("Pogrešno korisničko ime ili lozinka.", a.getMessage());
        assertEquals(a.getMessage(), b.getMessage());
        assertEquals(a.getCode(), b.getCode());
    }

    @Test
    void nullVrednostiSuPogresnaPrijava() {
        assertEquals(401, pokusajPrijave(null, null, ip).getCode());
        assertEquals(401, pokusajPrijave("nastavnik", null, ip).getCode());
    }

    @Test
    void predugaLozinkaJePogresna() {
        assertEquals(401, pokusajPrijave("nastavnik", LOZINKA + "x".repeat(200), ip).getCode());
    }

    @Test
    void deseteNeuspeleJeUredu_jedanaestaJeBlokiranaIZaTacnuLozinku() {
        for (int i = 0; i < 10; i++) {
            assertEquals(401, pokusajPrijave("nastavnik", "pogresna" + i, ip).getCode());
        }
        SystemException blok = pokusajPrijave("nastavnik", LOZINKA, ip);
        assertEquals(429, blok.getCode());
        assertEquals("Previše pokušaja. Pokušaj ponovo za 15 minuta.", blok.getMessage());
        // drugi IP prolazi
        String token = servis.prijavi("nastavnik", LOZINKA, noviIp(), UA);
        assertEquals(32, token.length());
        // posle 16 minuta isti IP ponovo može
        sat.pomeri(Duration.ofMinutes(16));
        assertEquals(32, servis.prijavi("nastavnik", LOZINKA, ip, UA).length());
    }

    @Test
    void uspesnaPrijavaNeTroshiLimit() {
        for (int i = 0; i < 15; i++) {
            assertEquals(32, servis.prijavi("nastavnik", LOZINKA, ip, UA).length());
        }
    }

    @Test
    void potvrdiProduzavaTekPosleDesetMinuta() {
        String token = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        NastavnickaSesija pocetna = repo.findByTokenHes(TokenHash.od(token)).orElseThrow();

        sat.pomeri(Duration.ofMinutes(5));
        NastavnickaSesija rano = servis.potvrdi(token).orElseThrow();
        assertEquals(pocetna.getId(), rano.getId());
        NastavnickaSesija uBaziRano = repo.findById(pocetna.getId()).orElseThrow();
        assertEquals(pocetna.getPoslednjiUlaz(), uBaziRano.getPoslednjiUlaz());
        assertEquals(pocetna.getIstice(), uBaziRano.getIstice());

        sat.pomeri(Duration.ofMinutes(6)); // ukupno 11 min
        NastavnickaSesija kasno = servis.potvrdi(token).orElseThrow();
        LocalDateTime sad = sada();
        assertEquals(sad, kasno.getPoslednjiUlaz());
        assertEquals(sad.plusDays(90), kasno.getIstice());
        NastavnickaSesija uBazi = repo.findById(pocetna.getId()).orElseThrow();
        assertTrue(uBazi.getPoslednjiUlaz().isAfter(pocetna.getPoslednjiUlaz()));
        assertTrue(uBazi.getIstice().isAfter(pocetna.getIstice()));
    }

    @Test
    void potvrdiOdbijaNepoznatOpozvanuIIstekluSesiju() {
        assertEquals(Optional.empty(), servis.potvrdi(null));
        assertEquals(Optional.empty(), servis.potvrdi(""));
        assertEquals(Optional.empty(), servis.potvrdi("nepoznat-token"));
        assertEquals(Optional.empty(), servis.potvrdi("a".repeat(32)));

        String opozvana = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        assertTrue(servis.potvrdi(opozvana).isPresent());
        servis.odjavi(opozvana);
        assertEquals(Optional.empty(), servis.potvrdi(opozvana));
        assertNotNull(repo.findByTokenHes(TokenHash.od(opozvana)).orElseThrow().getOpozvana());

        String istekla = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        jdbc.update("update nastavnicke_sesije set istice = ? where token_hes = ?", sada().minusMinutes(1), TokenHash.od(istekla));
        assertEquals(Optional.empty(), servis.potvrdi(istekla));
    }

    @Test
    void odjaviNepoznatTokenNeBaca() {
        assertDoesNotThrow(() -> servis.odjavi("nepoznat"));
        assertDoesNotThrow(() -> servis.odjavi(null));
    }

    @Test
    void sesijeOznacavajuTrenutnuIBrojeSamoVazece() {
        String a = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        sat.pomeri(Duration.ofMinutes(1));
        String b = servis.prijavi("nastavnik", LOZINKA, ip, "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0");
        String c = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        servis.odjavi(c);
        Long idA = repo.findByTokenHes(TokenHash.od(a)).orElseThrow().getId();
        Long idB = repo.findByTokenHes(TokenHash.od(b)).orElseThrow().getId();
        Long idC = repo.findByTokenHes(TokenHash.od(c)).orElseThrow().getId();

        List<SesijaInfo> lista = servis.sesije(idA).stream()
                .filter(s -> s.id().equals(idA) || s.id().equals(idB) || s.id().equals(idC)).toList();
        assertEquals(2, lista.size());
        SesijaInfo infoA = lista.stream().filter(s -> s.id().equals(idA)).findFirst().orElseThrow();
        SesijaInfo infoB = lista.stream().filter(s -> s.id().equals(idB)).findFirst().orElseThrow();
        assertTrue(infoA.trenutna());
        assertFalse(infoB.trenutna());
        assertEquals("Android · Chrome", infoA.opis());
        assertEquals("Windows · Edge", infoB.opis());
        assertNotNull(infoA.kreirano());
        assertNotNull(infoA.poslednjiUlaz());
    }

    @Test
    void opozoviJednuSesiju() {
        String t = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        Long id = repo.findByTokenHes(TokenHash.od(t)).orElseThrow().getId();
        servis.opozovi(id);
        assertEquals(Optional.empty(), servis.potvrdi(t));
        assertDoesNotThrow(() -> servis.opozovi(id)); // ponovo: bez greške
        SystemException e = assertThrows(SystemException.class, () -> servis.opozovi(-1L));
        assertEquals(404, e.getCode());
    }

    @Test
    void odjaviSveOsimOstavljaTrenutnu() {
        String trenutna = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        String druga = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        String treca = servis.prijavi("nastavnik", LOZINKA, ip, UA);
        Long idTrenutne = repo.findByTokenHes(TokenHash.od(trenutna)).orElseThrow().getId();

        int broj = servis.odjaviSveOsim(idTrenutne);

        // tuđe sesije iz drugih testova ne postoje (ocisti() ih briše), ali svaka vazeća osim trenutne je opozvana
        assertTrue(broj >= 2);
        assertTrue(servis.potvrdi(trenutna).isPresent());
        assertEquals(Optional.empty(), servis.potvrdi(druga));
        assertEquals(Optional.empty(), servis.potvrdi(treca));
    }
}
