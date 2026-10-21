package tri.novica.gfssystem.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PrilogCmd;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.Promena;
import tri.novica.gfssystem.utility.Serija;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Čitanje sirovih argumenata alata: merge patch (odsutno, {@code null}, vrednost), strogi objekti, serija. */
class McpPatchTest {

    static McpPatch patch(Map<String, Object> argumenti) {
        return McpPatch.iz(CallToolRequest.builder("probni").arguments(argumenti).build());
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static void ocekuj400(String poruka, Runnable r) {
        SystemException e = assertThrows(SystemException.class, r::run);
        assertEquals(400, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    @Test
    void odsutnoNijePostavljeno() {
        McpPatch p = patch(mapa("naziv", "x"));
        assertFalse(p.ima("opis"));
        assertTrue(p.ima("naziv"));
        Promena<String> opis = p.promena("opis", String.class, true);
        assertFalse(opis.postavljeno());
        assertEquals("staro", opis.primeni("staro"));
        assertEquals(Promena.bez(), opis);
    }

    @Test
    void nullBriseSamoBrisivoPolje() {
        Map<String, Object> m = new HashMap<>();
        m.put("opis", null);
        m.put("tekst", null);
        McpPatch p = patch(m);
        assertTrue(p.ima("opis"));
        assertEquals(Promena.na(null), p.promena("opis", String.class, true));
        assertNull(p.promena("opis", String.class, true).primeni("staro"));
        ocekuj400("Polje 'tekst' se ne briše.", () -> p.promena("tekst", String.class, false));
    }

    @Test
    void vrednostSePrimenjuje() {
        McpPatch p = patch(mapa("naziv", "Novo", "tezina", 3));
        assertEquals("Novo", p.promena("naziv", String.class, false).primeni("staro"));
        assertEquals(3, p.promena("tezina", Integer.class, true).vrednost());
    }

    @Test
    void promenaSaCitacemOdgovoraIPriloga() {
        Map<String, Object> m = new HashMap<>();
        m.put("odgovori", Map.of("a", Map.of("broj", 11)));
        m.put("prilozi", null);
        m.put("tekst", null);
        McpPatch p = patch(m);
        assertEquals(Promena.na(Map.of("a", new OdgovorPolja(11.0, null, null, null))),
                p.promena("odgovori", false, p::odgovori));
        assertEquals(Promena.na(null), p.promena("prilozi", true, p::prilozi));
        assertEquals(Promena.bez(), p.promena("naziv", true, p::prilozi));
        ocekuj400("Polje 'tekst' se ne briše.", () -> p.promena("tekst", false, p::odgovori));
        McpPatch losi = patch(mapa("odgovori", Map.of("a", Map.of("broj", 11, "x", 1)),
                "prilozi", List.of(Map.of("ime", "slika", "medijId", "m", "y", 1))));
        assertThrows(SystemException.class, () -> losi.promena("odgovori", false, losi::odgovori));
        ocekuj400("Nepoznato polje 'prilozi[0].y'.", () -> losi.promena("prilozi", true, losi::prilozi));
    }

    @Test
    void pogresanTipJeNeispravanFormat() {
        McpPatch p = patch(mapa("tezina", "3", "naziv", 5, "decimalni", 2.5, "zastavica", "true"));
        ocekuj400("Neispravan format podataka.", () -> p.promena("tezina", Integer.class, true));
        ocekuj400("Neispravan format podataka.", () -> p.promena("naziv", String.class, true));
        ocekuj400("Neispravan format podataka.", () -> p.promena("decimalni", Integer.class, true));
        ocekuj400("Neispravan format podataka.", () -> p.promena("zastavica", Boolean.class, true));
    }

    @Test
    void strogiObjekatOdbijaNepoznatoPoljeSaPutanjom() {
        McpPatch p = patch(mapa("slajd", mapa("tip", "INFO", "x", 1)));
        ocekuj400("Nepoznato polje 'slajd.x'.", () -> p.strogo("slajd", SlajdCmd.class));

        McpPatch ugnezden = patch(mapa("slajd", mapa("tip", "PITANJE", "pitanje", mapa("tacno", true))));
        ocekuj400("Nepoznato polje 'slajd.pitanje.tacno'.", () -> ugnezden.strogo("slajd", SlajdCmd.class));

        McpPatch lista = patch(mapa("prilozi", List.of(mapa("ime", "a", "medijId", "m"), mapa("ime", "b", "y", 1))));
        ocekuj400("Nepoznato polje 'prilozi[1].y'.", () -> lista.prilozi("prilozi"));
    }

    @Test
    void strogiObjekatOdbijaPogresanTipINullZaPrimitiv() {
        Map<String, Object> slajd = new HashMap<>(mapa("tip", "INFO"));
        slajd.put("postepeno", null);
        McpPatch p = patch(mapa("slajd", slajd));
        ocekuj400("Neispravan format podataka.", () -> p.strogo("slajd", SlajdCmd.class));

        McpPatch tip = patch(mapa("slajd", mapa("tip", "NEPOSTOJI")));
        ocekuj400("Neispravan format podataka.", () -> tip.strogo("slajd", SlajdCmd.class));

        McpPatch broj = patch(mapa("slajd", mapa("tip", "INFO", "naslov", 7)));
        ocekuj400("Neispravan format podataka.", () -> broj.strogo("slajd", SlajdCmd.class));
    }

    record SaPrimitivom(Integer a, boolean zastavica) {}

    @Test
    void izostavljenPrimitivJePodrazumevanIzricitNullJeGreska() {
        assertEquals(new SaPrimitivom(1, false), patch(mapa("o", mapa("a", 1))).strogo("o", SaPrimitivom.class));
        Map<String, Object> samoNull = new HashMap<>();
        samoNull.put("zastavica", null);
        ocekuj400("Neispravan format podataka.", () -> patch(mapa("o", samoNull)).strogo("o", SaPrimitivom.class));
        Map<String, Object> saA = new LinkedHashMap<>(mapa("a", 1));
        saA.put("zastavica", null);
        ocekuj400("Neispravan format podataka.", () -> patch(mapa("o", saA)).strogo("o", SaPrimitivom.class));
    }

    @Test
    void strogiObjekatSeCita() {
        McpPatch p = patch(mapa("slajd", mapa("tip", "INFO", "naslov", "Uvod", "postepeno", true)));
        SlajdCmd s = p.strogo("slajd", SlajdCmd.class);
        assertEquals(TipSlajda.INFO, s.tip());
        assertEquals("Uvod", s.naslov());
        assertTrue(s.postepeno());
        assertNull(p.strogo("nema", SlajdCmd.class));
    }

    @Test
    void poljaOdgovoriIPriloziKrozStrogiCitac() {
        McpPatch p = patch(mapa(
                "polja", List.of(mapa("kljuc", "a", "oznaka", "a)", "tip", "BROJ")),
                "odgovori", mapa("a", mapa("broj", 11.0)),
                "prilozi", List.of(mapa("ime", "slika1", "medijId", "123e4567-e89b-12d3-a456-426614174000"))));
        List<Polje> polja = p.polja("polja");
        assertEquals(1, polja.size());
        assertEquals(TipPolja.BROJ, polja.getFirst().tip());
        Map<String, OdgovorPolja> odgovori = p.odgovori("odgovori");
        assertEquals(11.0, odgovori.get("a").broj());
        assertEquals(List.of(new PrilogCmd("slika1", "123e4567-e89b-12d3-a456-426614174000")), p.prilozi("prilozi"));

        McpPatch los = patch(mapa("polja", List.of(mapa("kljuc", "a", "nepoznato", 1))));
        ocekuj400("Neispravan format podataka.", () -> los.polja("polja"));
    }

    @Test
    void serijaObrazac() {
        ocekuj400(Serija.PORUKA, () -> Serija.proveri("Uvoz-1"));
        ocekuj400(Serija.PORUKA, () -> Serija.proveri("ab"));
        ocekuj400(Serija.PORUKA, () -> Serija.proveri(null));
        ocekuj400(Serija.PORUKA, () -> Serija.proveri("a".repeat(61)));
        assertEquals("uvoz-kol1-2024", Serija.proveri("uvoz-kol1-2024"));
        assertEquals("Serija mora imati 3 do 60 malih slova, cifara ili crtica.", Serija.PORUKA);
    }

    @Test
    void bezArgumenataNistaNijePostavljeno() {
        McpPatch p = McpPatch.iz(CallToolRequest.builder("probni").build());
        assertFalse(p.ima("x"));
        assertFalse(p.promena("x", String.class, false).postavljeno());
    }
}
