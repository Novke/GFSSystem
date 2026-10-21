package tri.novica.gfssystem.service.lti;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import tri.novica.gfssystem.dto.lti.LtiRegistracijaCmd;
import tri.novica.gfssystem.dto.lti.LtiVrednostiZaMoodleInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.lti.LtiRegistracijaRepository;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Delovi {@link LtiRegistracijaServis} koji zavise od podešavanja (front URL, http, profil), sa ručno sklopljenim servisom. */
@SpringBootTest
class LtiRegistracijaServisIT {

    static final String PREFIKS = "http://lti-svc-";

    @Autowired LtiRegistracijaRepository repo;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void ocisti() {
        jdbc.update("delete from lti_registracije where izdavac like ?", PREFIKS + "%");
    }

    LtiRegistracijaServis servis(String frontUrl, boolean http, MockEnvironment okruzenje) {
        return new LtiRegistracijaServis(repo, clock, frontUrl, http, okruzenje);
    }

    @Test
    void vrednostiUzimajuPrviUnosListeBezZavrsneKose() {
        LtiVrednostiZaMoodleInfo v = servis("https://prvi.example.test/, https://drugi.example.test", false,
                new MockEnvironment()).vrednosti();
        assertEquals("https://prvi.example.test/api/public/lti/start", v.toolUrl());
        assertEquals("https://prvi.example.test/api/public/lti/start", v.redirectionUri());
        assertEquals("https://prvi.example.test/api/public/lti/prijava", v.initiateLoginUrl());
        assertEquals("https://prvi.example.test/api/public/lti/jwks", v.publicKeysetUrl());
    }

    @Test
    void httpJeDozvoljenSamoKadJeUkljucenIzricito() {
        String izdavac = PREFIKS + UUID.randomUUID().toString().substring(0, 8) + ".example.test";
        LtiRegistracijaCmd cmd = new LtiRegistracijaCmd("Lokalni", izdavac, "k", "1", "", "", null);
        SystemException e = assertThrows(SystemException.class,
                () -> servis("http://localhost:4200", false, new MockEnvironment()).napravi(cmd));
        assertEquals(400, e.getCode());
        var info = servis("http://localhost:4200", true, new MockEnvironment()).napravi(cmd);
        assertEquals(izdavac, info.izdavac());
        assertEquals(izdavac + "/mod/lti/certs.php", info.jwksUrl());
        assertTrue(info.aktivna());
    }

    @Test
    void httpUzProfilServerRusiStart() {
        MockEnvironment server = new MockEnvironment();
        server.setActiveProfiles("server");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> servis("https://gfs.example.test", true, server));
        assertTrue(e.getMessage().contains("gfs.lti.dozvoli-http"));
        assertDoesNotThrow(() -> servis("https://gfs.example.test", false, server));
    }
}
