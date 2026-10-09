package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KlijentIpTest {

    @Test
    void prviElementXForwardedFor() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("172.18.0.3");
        r.addHeader("X-Forwarded-For", " 203.0.113.9 , 10.0.0.1");
        assertEquals("203.0.113.9", KlijentIp.iz(r));
    }

    @Test
    void bezZaglavljaAdresaVeze() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("172.18.0.3");
        assertEquals("172.18.0.3", KlijentIp.iz(r));
        r.addHeader("X-Forwarded-For", "   ");
        assertEquals("172.18.0.3", KlijentIp.iz(r));
    }

    @Test
    void ogranicenoNa64Znaka() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-Forwarded-For", "a".repeat(500));
        assertEquals("a".repeat(64), KlijentIp.iz(r));
    }
}
