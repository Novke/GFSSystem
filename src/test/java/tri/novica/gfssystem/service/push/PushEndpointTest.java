package tri.novica.gfssystem.service.push;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static tri.novica.gfssystem.service.push.PushEndpoint.Provera.*;

/** Lista dozvoljenih push hostova (spec aplikacije 5.2, SSRF). */
class PushEndpointTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/dQw4w9WgXcQ:APA91bH",
            "https://fcm.googleapis.com:443/fcm/send/abc",
            "https://web.push.apple.com/QGkJ5e0zW5D0pQ",
            "https://api.push.apple.com/3/device/abc",
            "https://updates.push.services.mozilla.com/wpush/v2/gAAAAABk",
            "https://wns2-am3p.notify.windows.com/w/?token=BQYAAAB%2bA",
    })
    void dozvoljeni(String endpoint) {
        assertEquals(DOZVOLJEN, PushEndpoint.proveri(endpoint));
        assertTrue(PushEndpoint.uri(endpoint).isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "https://fcm.googleapis.com:8443/fcm/send/abc",
            "https://evil.example.com/fcm.googleapis.com",
            "https://fcm.googleapis.com.evil.com/x",
            "https://xfcm.googleapis.com/x",
            "https://push.apple.com/x",
            "https://evilpush.apple.com.attacker.net/x",
            "https://evil.com#.push.apple.com",
            "https://user@fcm.googleapis.com/x",
            "https://fcm.googleapis.com@evil.com/x",
            "https://FCM.googleapis.com/x",
            "https://fcm.googleapis.com./x",
            "https://127.0.0.1/x",
            "https://[::1]/x",
            "https://169.254.169.254/latest/meta-data",
            "https://fcm.googleapis.com/x#frag",
            "ftp://fcm.googleapis.com/x",
            "mailto:a@fcm.googleapis.com",
            "https://notify.windows.com/w",
            "https://a.notify.windows.com:80/w",
    })
    void nedozvoljeni(String endpoint) {
        assertNotEquals(DOZVOLJEN, PushEndpoint.proveri(endpoint));
        assertTrue(PushEndpoint.uri(endpoint).isEmpty());
    }

    @Test
    void neispravniOblici() {
        assertEquals(NEISPRAVAN, PushEndpoint.proveri(null));
        assertEquals(NEISPRAVAN, PushEndpoint.proveri(""));
        assertEquals(NEISPRAVAN, PushEndpoint.proveri("https://fcm.googleapis.com/a b"));
        assertEquals(NEISPRAVAN, PushEndpoint.proveri("https://fcm.googleapis.com/č"));
        assertEquals(NEISPRAVAN, PushEndpoint.proveri("https://fcm.googleapis.com/" + "a".repeat(PushEndpoint.MAX_DUZINA)));
        String granica = "https://fcm.googleapis.com/";
        assertEquals(DOZVOLJEN, PushEndpoint.proveri(granica + "a".repeat(PushEndpoint.MAX_DUZINA - granica.length())));
        assertEquals(NEPODRZAN, PushEndpoint.proveri("https://example.com/x"));
    }

    @Test
    void tragNemaEndpoint() {
        String e = "https://fcm.googleapis.com/fcm/send/TAJNI-TOKEN-123";
        String t = PushEndpoint.trag(e);
        assertTrue(t.startsWith("fcm.googleapis.com#"));
        assertEquals("fcm.googleapis.com#".length() + 8, t.length());
        assertFalse(t.contains("TAJNI"));
        assertEquals(PushEndpoint.hes(e).substring(0, 8), t.substring(t.indexOf('#') + 1));
    }
}
