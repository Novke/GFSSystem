package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.anketa.GlasCmd;
import tri.novica.gfssystem.service.anketa.AnketaService;
import tri.novica.gfssystem.service.cas.CasPodaci;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP alati brze ankete (spec brze ankete 8) kroz pravi {@code /mcp}: bar jedan poziv po alatu koji piše, čitanje šablona i
 * istorije sa brojevima po odgovoru, i da nijedan odgovor ne nosi studenta (id, ime) ni pojedinačan glas. Šabloni i folderi
 * su globalni, pa ih test briše pre i posle sebe; ankete idu sa grupom ({@link CasPodaci#obrisi}).
 */
class AnketeAlatiDbTest extends McpDbTestOsnova {

    @Autowired AnketaService ankete;
    @Autowired Clock clock;

    CasPodaci d;
    Long grupa;
    Long predavanje;
    Long ana;
    String serija;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from ankete_sabloni");
        jdbc.update("delete from ankete_folderi");
        d = new CasPodaci(jdbc);
        grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now(clock));
        ana = d.student(grupa, "Anastasija");
        jdbc.update("insert into cekiranja (predavanje_id, student_id, vreme) values (?, ?, ?)", predavanje, ana,
                Timestamp.valueOf(LocalDateTime.now(clock).minusMinutes(5)));
        serija = "anketa-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from ankete_sabloni");
        jdbc.update("delete from ankete_folderi");
        d.obrisi();
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    JsonNode uspeh(String alat, Map<String, Object> argumenti) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertFalse(McpTestKlijent.greska(r), r.toString());
        String tekst = McpTestKlijent.tekst(r);
        for (String zabranjeno : List.of("studentId", "Anastasija", "Čekić", "mojGlas", "indeks")) {
            assertFalse(tekst.contains(zabranjeno), alat + ": " + zabranjeno + " u " + tekst);
        }
        return McpTestKlijent.json(r);
    }

    String greska(String alat, Map<String, Object> argumenti) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertTrue(McpTestKlijent.greska(r), r.toString());
        return McpTestKlijent.tekst(r);
    }

    @Test
    void sabloniFolderiIPreciceKrozAlate() {
        JsonNode f = uspeh("sacuvaj_folder_ankete", mapa("serija", serija, "naziv", "Tok časa"));
        long folderId = f.path("id").asLong();
        assertEquals(folderId, uspeh("sacuvaj_folder_ankete", mapa("serija", serija, "naziv", "tok ČASA")).path("id").asLong(),
                "isti naziv: isti folder");
        assertEquals("Tok časa i pauze", uspeh("sacuvaj_folder_ankete", mapa("serija", serija, "id", folderId,
                "naziv", "Tok časa i pauze")).path("naziv").asString());

        JsonNode s = uspeh("sacuvaj_sablon_ankete", mapa("serija", serija, "pitanje", "Kolokvijum: koji datum?",
                "opcije", List.of("Opcija 1", "Opcija 2"), "folder", "Organizacija predmeta", "publika", "GRUPA",
                "rokSati", 24, "precica", true, "ikonica", "📅", "boja", "VISNJA", "kratakNaziv", "Kolokvijum datum"));
        long sablonId = s.path("id").asLong();
        assertEquals("GRUPA", s.path("publika").asString());
        assertEquals(24, s.path("rokSati").asInt());
        assertTrue(s.path("precica").asBoolean());
        JsonNode opet = uspeh("sacuvaj_sablon_ankete", mapa("serija", serija, "pitanje", "kolokvijum: koji datum?",
                "opcije", List.of("Opcija 1", "Opcija 2", "Opcija 3"), "folder", "Organizacija predmeta", "publika", "GRUPA",
                "rokSati", 48, "precica", true, "boja", "VISNJA"));
        assertEquals(sablonId, opet.path("id").asLong(), "ponovljen poziv sa istim pitanjem menja isti šablon");
        assertEquals(3, opet.path("opcije").size());

        JsonNode sve = uspeh("ankete_sabloni", Map.of());
        assertEquals(2, sve.path("folderi").size());
        assertEquals(1, sve.path("sabloni").size());
        assertEquals(1, sve.path("sabloni").get(0).path("precicaRb").asInt());

        JsonNode bez = uspeh("poredjaj_precice", mapa("serija", serija, "ids", List.of()));
        assertFalse(bez.path("sabloni").get(0).path("precica").asBoolean());
        assertTrue(greska("sacuvaj_sablon_ankete", mapa("serija", serija, "pitanje", "Samo jedan?", "opcije", List.of("Da")))
                .contains("od 2 do 10 odgovora"));
        assertTrue(greska("poredjaj_precice", mapa("serija", serija, "ids", List.of(-1))).contains("Prečice moraju"));

        assertEquals(sablonId, uspeh("obrisi_sablon_ankete", mapa("serija", serija, "id", sablonId)).path("obrisan").asLong());
        assertEquals(folderId, uspeh("obrisi_folder_ankete", mapa("serija", serija, "id", folderId)).path("obrisan").asLong());
        assertEquals("Šablon ankete nije pronađen.", greska("obrisi_sablon_ankete", mapa("serija", serija, "id", sablonId)));
        assertEquals(0, uspeh("ankete_sabloni", Map.of()).path("sabloni").size());
    }

    @Test
    void pokreniPrikaziZatvoriIIstorija() {
        JsonNode a = uspeh("pokreni_anketu", mapa("serija", serija, "pitanje", "Kakav je tempo?",
                "opcije", List.of("Prebrzo", "Taman", "Sporo"), "predavanjeId", predavanje, "trajanjeSekunde", 600));
        long id = a.path("id").asLong();
        assertEquals("CLAUDE", a.path("izvor").asString());
        assertTrue(a.path("otvorena").asBoolean());
        assertEquals(1, a.path("brojMogucih").asInt());
        ankete.glasaj(ana, id, new GlasCmd(List.of(1)));

        JsonNode r = uspeh("prikazi_rezultat_ankete", mapa("serija", serija, "id", id));
        assertTrue(r.path("rezultatPrikazan").asBoolean(), "prikazan podrazumevano true");
        assertFalse(uspeh("prikazi_rezultat_ankete", mapa("serija", serija, "id", id, "prikazan", false))
                .path("rezultatPrikazan").asBoolean());
        JsonNode z = uspeh("zatvori_anketu", mapa("serija", serija, "id", id));
        assertFalse(z.path("otvorena").asBoolean());
        assertFalse(z.path("zatvoreno").isNull());
        assertEquals(z.path("zatvoreno").asString(), uspeh("zatvori_anketu", mapa("serija", serija, "id", id))
                .path("zatvoreno").asString(), "ponovljeno zatvaranje bez efekta");

        String rok = LocalDateTime.now(clock).plusDays(2).truncatedTo(ChronoUnit.MINUTES).toString();
        JsonNode g = uspeh("pokreni_anketu", mapa("serija", serija, "pitanje", "Kolokvijum?", "opcije",
                List.of("12.12.", "19.12."), "publika", "GRUPA", "grupaId", grupa, "rok", rok));
        assertEquals(rok, g.path("rok").asString().substring(0, rok.length()));
        JsonNode g2 = uspeh("pokreni_anketu", mapa("serija", serija, "pitanje", "Nadoknada?", "opcije", List.of("Pon", "Sre"),
                "publika", "GRUPA", "predavanjeId", predavanje, "rokSati", 48));
        assertEquals(grupa, g2.path("grupaId").asLong(), "grupa iz predavanja");
        assertTrue(greska("pokreni_anketu", mapa("serija", serija, "pitanje", "K?", "opcije", List.of("a", "b"), "publika",
                "GRUPA", "grupaId", grupa, "rok", "sutra u 8")).startsWith("Neispravan parametar: rok"));
        assertEquals("Predavanje nije pronađeno.", greska("pokreni_anketu", mapa("serija", serija, "pitanje", "K?",
                "opcije", List.of("a", "b"), "predavanjeId", -1)));

        JsonNode ist = uspeh("ankete_istorija", mapa("predmetId", d.predmetId));
        assertEquals(2, ist.size(), "predmet: samo ankete sa predavanja tog predmeta (grupa bez predavanja nema predmet)");
        JsonNode tempo = ist.get(1);
        assertEquals(id, tempo.path("id").asLong(), "najnovije prve");
        assertEquals(List.of(0, 1, 0), List.of(tempo.path("glasovi").get(0).asInt(), tempo.path("glasovi").get(1).asInt(),
                tempo.path("glasovi").get(2).asInt()));
        assertEquals(1, tempo.path("brojGlasalo").asInt());
        assertEquals(3, uspeh("ankete_istorija", mapa("grupaId", grupa)).size());
        assertEquals(2, uspeh("ankete_istorija", mapa("predavanjeId", predavanje)).size());
        assertEquals("Anketa nije pronađena.", greska("zatvori_anketu", mapa("serija", serija, "id", -1)));
    }
}
