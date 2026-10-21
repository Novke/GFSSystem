package tri.novica.gfssystem.service.lti;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tri.novica.gfssystem.exceptions.SystemException;

import static org.junit.jupiter.api.Assertions.*;

/** Provera LTI adresa bez Spring-a: samo {@code https://} (http samo kad ga test izričito dozvoli). */
class LtiUrlTest {

    @Test
    void httpsProlazi() {
        assertEquals("https://moodle.example.test/mod/lti/auth.php",
                LtiUrl.proveri("https://moodle.example.test/mod/lti/auth.php", false, "Auth URL"));
    }

    @Test
    void razmaciSeOdsecaju() {
        assertEquals("https://moodle.example.test", LtiUrl.proveri("  https://moodle.example.test  ", false, "Izdavač"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://moodle.example.test",
            "ftp://moodle.example.test",
            "javascript:alert(1)",
            "//moodle.example.test",
            "moodle.example.test",
            "https://",
            "https:///putanja",
            "https://korisnik:lozinka@moodle.example.test",
            "https://moodle.example.test/#frag",
            "https://moodle .example.test",
            "file:///etc/passwd"})
    void drugoSeOdbija(String url) {
        SystemException e = assertThrows(SystemException.class, () -> LtiUrl.proveri(url, false, "Izdavač"));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().startsWith("Izdavač"), e.getMessage());
    }

    @Test
    void httpSamoKadJeDozvoljen() {
        assertEquals("http://localhost:8080/jwks", LtiUrl.proveri("http://localhost:8080/jwks", true, "JWKS URL"));
        assertThrows(SystemException.class, () -> LtiUrl.proveri("ftp://localhost/jwks", true, "JWKS URL"));
    }

    @Test
    void izvedenaAdresaBezZavrsneKoseCrte() {
        assertEquals("https://m.example.test/mod/lti/auth.php", LtiUrl.izvedi("https://m.example.test/", "/mod/lti/auth.php"));
        assertEquals("https://m.example.test/sub/mod/lti/certs.php",
                LtiUrl.izvedi("https://m.example.test/sub", "/mod/lti/certs.php"));
    }
}
