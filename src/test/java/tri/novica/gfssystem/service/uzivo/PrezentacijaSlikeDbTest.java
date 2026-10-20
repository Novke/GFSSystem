package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.dto.uzivo.platno.Platno;
import tri.novica.gfssystem.dto.uzivo.platno.Potez;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.validation.SlajdPP;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Markdown slike slajda nad pravom bazom (spec 6.5): cilj {@code api/public/mediji/<uuid>} mora biti postojeći medij vrste
 * {@code SLIKA} (render SVG banke i nepostojeći UUID su 400), za svaki upis slajda (dodavanje, izmena, uvoz), i čitanje
 * jednog slajda ({@link PrezentacijaService#slajd}) sa potezima crteža. Bez {@code @Transactional}: svaki poziv servisa se
 * zaista commit-uje, pa test čisti za sobom.
 */
@SpringBootTest
class PrezentacijaSlikeDbTest {

    @Autowired PrezentacijaService service;
    @Autowired PredmetRepository predmetRepository;
    @Autowired MedijRepository medijRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    Predmet predmet;
    String slikaId;
    String svgId;
    final List<Long> prezentacije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Markdown slike DB test " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
        slikaId = medij(VrstaMedija.SLIKA, "tabla.png", "image/png");
        svgId = medij(VrstaMedija.RENDER_SVG, "render.svg", "image/svg+xml");
    }

    @AfterEach
    void tearDown() {
        for (Long id : prezentacije) {
            try {
                service.obrisi(id);
            } catch (SystemException e) {
                // već obrisana u testu
            }
        }
        predmetRepository.deleteById(predmet.getId());
        medijRepository.deleteById(slikaId);
        medijRepository.deleteById(svgId);
    }

    String medij(VrstaMedija vrsta, String naziv, String mime) {
        Medij m = new Medij();
        m.setId(UUID.randomUUID().toString());
        m.setNaziv(naziv);
        m.setMime(mime);
        m.setVelicina(10);
        m.setVrsta(vrsta);
        m.setKreirano(LocalDateTime.now(clock));
        return medijRepository.save(m).getId();
    }

    Long nova(String naziv) {
        Long id = service.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), naziv, null, null)).id();
        prezentacije.add(id);
        return id;
    }

    static String slika(String medijId) {
        return "![slika](api/public/mediji/" + medijId + ")";
    }

    static SlajdCmd info(String sadrzaj) {
        return new SlajdCmd(TipSlajda.INFO, "Naslov", sadrzaj, null, null, false, null, null, null);
    }

    static SlajdCmd pitanje(String tekst, String opcija) {
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, new PitanjeCmd(TipPitanja.JEDAN_TACAN,
                tekst, null, null, List.of(new OpcijaCmd(opcija, true), new OpcijaCmd("ne", false)),
                null, null, null, null, null, null, null, null), null, null);
    }

    long brojSlajdova(Long prezentacijaId) {
        Long n = jdbc.queryForObject("select count(*) from slajdovi where prezentacija_id = ?", Long.class, prezentacijaId);
        return n == null ? 0 : n;
    }

    static void odbijeno(Executable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv);
        assertEquals(400, e.getCode());
        assertEquals(SlajdPP.SLIKA_VAN_GFS, e.getMessage());
    }

    @Test
    void renderSvgUMarkdownuJe400KrozDodavanjeIIzmenu() {
        Long id = nova("Render u tekstu");
        odbijeno(() -> service.dodajSlajd(id, info("Pogledaj " + slika(svgId)), null));
        assertEquals(0, brojSlajdova(id));

        SlajdDetails d = service.dodajSlajd(id, info("bez slike"), null);
        odbijeno(() -> service.izmeniSlajd(d.id(), info(slika(svgId))));
        assertEquals("bez slike", service.slajd(d.id()).sadrzaj());
    }

    @Test
    void slikaIzGfsaProlazi() {
        Long id = nova("Slika u tekstu");
        SlajdDetails d = service.dodajSlajd(id, info("Pogledaj " + slika(slikaId)), null);
        assertEquals("Pogledaj " + slika(slikaId), d.sadrzaj());
        SlajdDetails p = service.izmeniSlajd(d.id(), pitanje("Šta je ovo? " + slika(slikaId), slika(slikaId)));
        assertEquals(TipSlajda.PITANJE, p.tip());
    }

    @Test
    void nepostojeciMedijJe400() {
        Long id = nova("Nepostojeći medij");
        odbijeno(() -> service.dodajSlajd(id, info(slika(UUID.randomUUID().toString())), null));
        // jedna postoji, druga ne: i dalje 400
        odbijeno(() -> service.dodajSlajd(id, info(slika(slikaId) + " " + slika(UUID.randomUUID().toString())), null));
        assertEquals(0, brojSlajdova(id));
    }

    @Test
    void spoljnaSlikaUOpcijiPitanjaJe400() {
        Long id = nova("Opcija sa slikom");
        odbijeno(() -> service.dodajSlajd(id, pitanje("Koja?", "![a](https://x.invalid/a.png)"), null));
        odbijeno(() -> service.dodajSlajd(id, pitanje("Koja? ![a](https://x.invalid/a.png)", "da"), null));
        assertEquals(0, brojSlajdova(id));
    }

    @Test
    void uvozSaSlikomVanGfsaJe400SaBrojemSlajda() {
        Long id = nova("Uvoz sa slikom");
        SystemException e = assertThrows(SystemException.class, () -> service.uveziSlajdove(id,
                new UvozSlajdovaCmd(null, List.of(info("prvi"), info(slika(svgId))))));
        assertEquals(400, e.getCode());
        assertEquals("Slajd 2: " + SlajdPP.SLIKA_VAN_GFS, e.getMessage());
        assertEquals(0, brojSlajdova(id));
    }

    @Test
    void slajdPlatnoVracaPoteze() {
        Long id = nova("Platno sa potezima");
        List<Potez> potezi = List.of(new Potez("#FF0000", 2.0, List.of(0.0, 0.0, 10.0, 5.0)));
        Platno platno = PrezentacijaServiceDbTest.saCrtezom(PlatnoTestPodaci.ispravno(slikaId, slikaId), 100.0, 50.0, potezi);
        SlajdDetails d = service.dodajSlajd(id, new SlajdCmd(TipSlajda.PLATNO, null, null, null, "beleška", false, null,
                PlatnoTestPodaci.cvor(platno), null), null);

        SlajdDetails procitan = service.slajd(d.id());
        assertEquals(d, procitan);
        assertEquals(TipSlajda.PLATNO, procitan.tip());
        assertEquals(potezi, ((tri.novica.gfssystem.dto.uzivo.platno.CrtezSloj) procitan.platno().slojevi().get(6)).potezi());
    }

    @Test
    void nepostojeciSlajdJe404() {
        SystemException e = assertThrows(SystemException.class, () -> service.slajd(Long.MAX_VALUE));
        assertEquals(404, e.getCode());
        assertEquals("Slajd nije pronađen.", e.getMessage());
    }
}
