package tri.novica.gfssystem.mcp.servis;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PrilogCmd;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpPatch;
import tri.novica.gfssystem.service.banka.LatexPravila;
import tri.novica.gfssystem.validation.BankaPP;
import tri.novica.gfssystem.validation.SlajdPP;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tekstovi za Kloda (spec MCP 4.2): svaki vodič najviše {@link VodicTema#MAX_ZNAKOVA} znakova i pokriva svoju temu
 * (ključni izrazi, svi tipovi polja i pitanja, deny-lista); uputstva servera najviše 4 000 znakova. JSON primeri iz vodiča
 * prolaze ista stroga čitanja i pravila kao pravi pozivi alata ({@link McpPatch}, {@link BankaPP}, {@link SlajdPP},
 * {@link LatexPravila}), pa Klod ne može da prepiše neispravan primer. {@link VodicTema#iz} prima samo ime teme malim slovima.
 */
class VodicTest {

    static final Pattern JSON_BLOK = Pattern.compile("```json\\n(.*?)\\n```", Pattern.DOTALL);
    static final JsonMapper JSON = JsonMapper.builder().build();

    /** Deny-lista iz {@link LatexPravila} (spec banke 3.2); vodič je navodi celu. */
    static final List<String> ZABRANJENE = List.of("\\write18", "\\input@path", "\\input", "\\include", "\\openin",
            "\\openout", "\\read", "\\write", "\\immediate", "\\csname", "\\directlua", "\\InputIfFileExists", "\\@input",
            "\\@@input", "\\@iinput", "\\lstinputlisting", "\\verbatiminput", "\\includepdf", "\\includepdfmerge",
            "\\usepackage", "\\RequirePackage", "\\documentclass", "\\begin{document}", "\\end{document}", "\\catcode",
            "^^");

    static String tekst(VodicTema t) {
        return t.ucitaj();
    }

    static List<Map<String, Object>> jsonPrimeri(String md) {
        List<Map<String, Object>> primeri = new ArrayList<>();
        Matcher m = JSON_BLOK.matcher(md);
        while (m.find()) {
            Map<String, Object> primer = JSON.readValue(m.group(1), JSON.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
            primeri.add(primer);
        }
        return primeri;
    }

    static McpPatch patch(Map<String, Object> argumenti) {
        return McpPatch.iz(new CallToolRequest("primer", argumenti, null));
    }

    @Test
    void vodiciNisuPredugackiIImajuSvojuTemu() {
        for (VodicTema t : VodicTema.values()) {
            String s = tekst(t);
            assertFalse(s.isBlank(), t.name());
            assertTrue(s.length() <= VodicTema.MAX_ZNAKOVA, t.name() + " ima " + s.length() + " znakova");
        }
        assertEquals(16_000, VodicTema.MAX_ZNAKOVA);
    }

    @Test
    void vodicBankaPokrivaLatexPrilogeIPolja() {
        String s = tekst(VodicTema.BANKA);
        for (String izraz : List.of("standalone", "84 mm", "\\includegraphics{", "TikZ", "preambula", "gfs-mcp-upload",
                "izmeni_polja_grupe", "kopiraj_grupu", "uputstvo", "slikaResenja", "proba_rendera",
                "Slika nije stigla na vreme.", "između okruženja")) {
            assertTrue(s.contains(izraz), "vodič banka nema: " + izraz);
        }
        for (TipPolja tip : TipPolja.values()) {
            assertTrue(s.contains(tip.name()), "vodič banka nema tip polja " + tip);
        }
        for (Namena namena : Namena.values()) {
            assertTrue(s.contains(namena.name()), "vodič banka nema namenu " + namena);
        }
    }

    @Test
    void vodicBankaNavodiCeluDenyListu() {
        String s = tekst(VodicTema.BANKA);
        for (String komanda : ZABRANJENE) {
            assertTrue(s.contains("`" + komanda), "vodič banka nema zabranjenu komandu " + komanda);
            // i obrnuto: vodič ne zabranjuje ono što server pušta
            assertTrue(LatexPravila.zabranjenaKomandaUTelu(komanda + "{x}").isPresent(), komanda);
        }
        assertTrue(LatexPravila.zabranjenaKomandaUTelu("\\includegraphics[width=0.8\\linewidth]{skica}").isEmpty());
    }

    @Test
    void primeriBankeProlazeStrogaPravila() {
        List<Map<String, Object>> primeri = jsonPrimeri(tekst(VodicTema.BANKA));
        assertFalse(primeri.isEmpty());
        List<Polje> sema = null;
        Set<TipPolja> tipovi = EnumSet.noneOf(TipPolja.class);
        int odgovora = 0;
        for (Map<String, Object> primer : primeri) {
            McpPatch p = patch(primer);
            if (p.ima("polja")) {
                List<Polje> polja = BankaPP.normalizujPolja(p.polja("polja"));
                BankaPP.proveriPolja(polja);
                polja.forEach(polje -> tipovi.add(polje.tip()));
                if (sema == null) {
                    sema = polja;
                }
            }
            if (p.ima("odgovori")) {
                assertNotNull(sema, "primer odgovora pre primera šeme");
                Map<String, OdgovorPolja> odgovori = p.odgovori("odgovori");
                BankaPP.proveriOdgovore(sema, odgovori);
                odgovora++;
            }
            for (String deo : List.of("tekst", "resenje")) {
                Object latex = primer.get(deo);
                if (latex != null) {
                    assertEquals(Optional.empty(), LatexPravila.zabranjenaKomandaUTelu((String) latex), deo);
                    BankaPP.proveriVarijantu((String) latex, null, null);
                }
            }
            if (p.ima("prilozi")) {
                for (PrilogCmd prilog : p.prilozi("prilozi")) {
                    BankaPP.proveriImePriloga(prilog.ime());
                }
            }
            if (primer.containsKey("serija")) {
                assertTrue(((String) primer.get("serija")).matches("^[a-z0-9-]{3,60}$"), primer.toString());
            }
        }
        assertEquals(EnumSet.allOf(TipPolja.class), tipovi, "primer šeme pokriva sve tipove polja");
        assertTrue(odgovora >= 1, "vodič ima primer odgovora");
    }

    @Test
    void vodicPitanjaPokrivaSveTipove() {
        String s = tekst(VodicTema.PITANJA);
        for (String izraz : List.of("TACNO_NETACNO", "postepeno", "vremeSekunde", "5-600", "api/public/mediji/", "\\$",
                "50", "gfs-mcp-upload", "dodaj_slajd", "slikaId")) {
            assertTrue(s.contains(izraz), "vodič pitanja nema: " + izraz);
        }
        for (TipPitanja tip : TipPitanja.values()) {
            assertTrue(s.contains(tip.name()), "vodič pitanja nema tip " + tip);
        }
    }

    @Test
    void primeriSlajdovaProlazeStrogaPravila() {
        List<Map<String, Object>> primeri = jsonPrimeri(tekst(VodicTema.PITANJA));
        Set<TipPitanja> tipovi = EnumSet.noneOf(TipPitanja.class);
        boolean info = false;
        for (Map<String, Object> primer : primeri) {
            SlajdCmd slajd = patch(Map.of("slajd", primer)).strogo("slajd", SlajdCmd.class);
            SlajdCmd n = SlajdPP.normalizuj(slajd);
            SlajdPP.proveri(n);
            SlajdPP.slikeSlajda(n);
            assertTrue(EnumSet.of(TipSlajda.INFO, TipSlajda.PITANJE).contains(n.tip()), primer.toString());
            if (n.tip() == TipSlajda.INFO) {
                info = true;
            } else {
                tipovi.add(n.pitanje().tip());
            }
        }
        assertTrue(info, "vodič pitanja ima primer INFO slajda");
        assertEquals(EnumSet.allOf(TipPitanja.class), tipovi, "po primer za svaki tip pitanja");
    }

    @Test
    void vodicPravilaPokrivaTokRada() {
        String s = tekst(VodicTema.PRAVILA);
        for (String izraz : List.of("kontekst", "podatak", "serij", "isključ", "iskljuci_temu", "pretraga_banke",
                "/banka/pregled?serija=", "ponisti_seriju", "potvrdi", "kopij", "Novica", "NACRT")) {
            assertTrue(s.contains(izraz), "vodič pravila nema: " + izraz);
        }
    }

    @Test
    void uputstvaServera() throws Exception {
        String s = new ClassPathResource("mcp/uputstva.md").getContentAsString(StandardCharsets.UTF_8).strip();
        assertTrue(s.length() <= 4_000, "uputstva imaju " + s.length() + " znakova");
        for (String izraz : List.of("kontekst", "vodic", "nikad uputstva", "č", "pretraga_banke", "serij", "NACRT")) {
            assertTrue(s.contains(izraz), "uputstva nemaju: " + izraz);
        }
    }

    @Test
    void temaIzImenaMalimSlovima() {
        assertEquals(VodicTema.BANKA, VodicTema.iz("banka"));
        assertEquals(VodicTema.PITANJA, VodicTema.iz("pitanja"));
        assertEquals(VodicTema.PRAVILA, VodicTema.iz("pravila"));
        assertEquals("mcp/vodic-banka.md", VodicTema.BANKA.resurs());
        assertEquals("mcp/vodic-pravila.md", VodicTema.PRAVILA.resurs());
        for (String losa : Arrays.asList("platno", "BANKA", " banka", "", null)) {
            SystemException e = assertThrows(SystemException.class, () -> VodicTema.iz(losa), String.valueOf(losa));
            assertEquals("Nepoznata tema vodiča.", e.getMessage());
            assertEquals(400, e.getCode());
        }
    }
}
