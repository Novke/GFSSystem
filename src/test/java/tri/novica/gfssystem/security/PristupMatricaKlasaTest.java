package tri.novica.gfssystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static tri.novica.gfssystem.security.KlasaPristupa.*;

/**
 * {@link PristupMatrica#klasa} prema spec-u, sekcija 3 (bez Spring konteksta). Test nad svim stvarnim endpointima je
 * {@code PristupMatricaTest}; ovde su redovi tabele, granice uzoraka i nepreklapanje klasa.
 */
class PristupMatricaKlasaTest {

    static final HttpMethod GET = HttpMethod.GET;
    static final HttpMethod POST = HttpMethod.POST;
    static final HttpMethod PUT = HttpMethod.PUT;
    static final HttpMethod DELETE = HttpMethod.DELETE;

    @Test
    void anonimnoPoSpecu() {
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/public/upis/abc"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(POST, "/public/upis/{token}"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/public/uzivo/123456"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/public/uzivo/{kod}/ja"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(POST, "/public/uzivo/{kod}/prijava"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/public/mediji/{uuid}"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/public/ws"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(POST, "/public/nalog/kod"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(POST, "/public/nalog/prijava"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(POST, "/public/nalog/pomoc"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/public/nalog/stanje"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/public/lti/prijava"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(POST, "/public/lti/prijava"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(POST, "/public/lti/start"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/public/lti/jwks"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(POST, "/auth/prijava"));
        assertEquals(ANONIMNO, PristupMatrica.klasa(GET, "/auth/ja"));
    }

    @Test
    void studentPoSpecu() {
        assertEquals(STUDENT, PristupMatrica.klasa(GET, "/public/nalog/ja"));
        assertEquals(STUDENT, PristupMatrica.klasa(POST, "/public/nalog/odjava"));
        assertEquals(STUDENT, PristupMatrica.klasa(POST, "/public/nalog/odjava-svuda"));
        assertEquals(STUDENT, PristupMatrica.klasa(POST, "/public/lti/povezi"));
    }

    @Test
    void mcpPoSpecu() {
        assertEquals(MCP, PristupMatrica.klasa(POST, "/mcp"));
        assertEquals(MCP, PristupMatrica.klasa(GET, "/mcp"));
        assertEquals(MCP, PristupMatrica.klasa(POST, "/mcp/prilozi"));
        assertEquals(MCP, PristupMatrica.klasa(GET, "/mcp/slike/{id}"));
    }

    @Test
    void sveOstaloJeAdmin() {
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/studenti"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/ws"));
        assertEquals(ADMIN, PristupMatrica.klasa(POST, "/mediji"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/banka/grupe/{id}"));
        assertEquals(ADMIN, PristupMatrica.klasa(PUT, "/predmeti/{id}/latex-preambula"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/obavestenja"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/lti/registracije"));
        assertEquals(ADMIN, PristupMatrica.klasa(DELETE, "/studenti/{id}/nalog/moodle"));
        assertEquals(ADMIN, PristupMatrica.klasa(POST, "/auth/odjava"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/auth/sesije"));
        assertEquals(ADMIN, PristupMatrica.klasa(DELETE, "/auth/sesije/{id}"));
        assertEquals(ADMIN, PristupMatrica.klasa(POST, "/auth/sesije/odjavi-sve"));
    }

    @Test
    void metodaIGraniceUzorka() {
        // pogrešna metoda na javnoj putanji nije javna
        assertEquals(ADMIN, PristupMatrica.klasa(DELETE, "/public/upis/abc"));
        assertEquals(ADMIN, PristupMatrica.klasa(PUT, "/public/mediji/{uuid}"));
        assertEquals(ADMIN, PristupMatrica.klasa(POST, "/public/nalog/stanje"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/public/lti/start"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/auth/prijava"));
        // bez trailing slash-a i bez dubljih putanja (kao Spring MVC)
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/public/upis/abc/"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/public/upis/abc/def"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/public/upis"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/public/ws/x"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/PUBLIC/upis/abc"));
        assertEquals(ADMIN, PristupMatrica.klasa(GET, "/mcpx"));
        // bez metode (null) samo uzorci koji važe za sve metode
        assertEquals(ANONIMNO, PristupMatrica.klasa(null, "/public/ws"));
        assertEquals(ADMIN, PristupMatrica.klasa(null, "/public/upis/abc"));
    }

    @Test
    void klaseSeNePreklapaju() {
        List<String> preklapanja = new ArrayList<>();
        proveri("ANONIMNO", PristupMatrica.anonimno(), preklapanja);
        proveri("STUDENT", PristupMatrica.student(), preklapanja);
        proveri("MCP", PristupMatrica.mcp(), preklapanja);
        assertTrue(preklapanja.isEmpty(), "uzorci pripadaju dvema klasama: " + preklapanja);
    }

    private static void proveri(String ime, List<PristupMatrica.Pravilo> pravila, List<String> preklapanja) {
        KlasaPristupa ocekivano = KlasaPristupa.valueOf(ime);
        for (PristupMatrica.Pravilo p : pravila) {
            List<HttpMethod> metode = p.metod() == null
                    ? List.of(GET, POST, PUT, DELETE, HttpMethod.PATCH) : List.of(p.metod());
            for (HttpMethod m : metode) {
                KlasaPristupa k = PristupMatrica.klasa(m, p.uzorak());
                if (k != ocekivano) preklapanja.add(m + " " + p.uzorak() + " -> " + k + " (očekivano " + ime + ")");
                if (PristupMatrica.pogodjene(m, p.uzorak()).size() != 1) {
                    preklapanja.add(m + " " + p.uzorak() + " pogađa " + PristupMatrica.pogodjene(m, p.uzorak()));
                }
            }
        }
    }

    @Test
    void nijedanJavniUzorakNijeAdminINijedanAdminNijeJavan() {
        for (PristupMatrica.Pravilo p : PristupMatrica.anonimno()) {
            assertTrue(p.uzorak().startsWith("/public/") || p.uzorak().startsWith("/auth/"), p.toString());
        }
        for (PristupMatrica.Pravilo p : PristupMatrica.student()) {
            assertTrue(p.uzorak().startsWith("/public/"), p.toString());
        }
    }
}
