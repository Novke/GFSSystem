package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.mcp.servis.McpBanka;
import tri.novica.gfssystem.mcp.servis.McpKontekst;
import tri.novica.gfssystem.mcp.servis.McpPrezentacije;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skup javnih metoda svake fasade je zakucan (spec MCP 2): nova metoda obara test dok se svesno ne doda ovde (i, ako
 * zove novu metodu servisa, u {@code McpOpsegTest.DOZVOLJENE_METODE}). Ključ je {@code ime(Tip, Tip)} prostih imena.
 */
class McpFasadeTest {

    static final Map<Class<?>, Set<String>> METODE = Map.of(
            McpKontekst.class, Set.of("kontekst()", "vodic(String)", "preambula(Long)", "domaciTekstovi(Long, int)"),
            McpBanka.class, Set.of("stablo(Long, Namena)", "grupa(Long)", "varijanta(Long, ZahtevSlike, McpPoziv)",
                    "pretraga(BankaFilter, int)", "iskljuceneTeme(Long)", "iskljuciTemu(Long, String, Long, String)",
                    "ukljuciTemu(Long)", "napraviGrupu(GrupaCmd, String)", "izmeniGrupu(Long, GrupaPatch, String)",
                    "izmeniPoljaGrupe(Long, List, String)", "premestiGrupu(Long, PremestiCmd, String)",
                    "obrisiGrupu(Long, String)", "kopirajGrupu(Long, Namena, Long, String)",
                    "ponistiSeriju(String, boolean)",
                    "napraviVarijantu(Long, VarijantaCmd, String, ZahtevSlike, McpPoziv)",
                    "izmeniVarijantu(Long, VarijantaPatch, String, ZahtevSlike, McpPoziv)",
                    "duplirajVarijantu(Long, String)", "obrisiVarijantu(Long, String)",
                    "probaRendera(RenderProbaCmd, ZahtevSlike)"),
            McpPrezentacije.class, Set.of("prezentacije(Long)", "prezentacija(Long, int)", "slajd(Long)",
                    "napraviPrezentaciju(CreatePrezentacijaCmd, String)", "dodajSlajd(Long, Long, SlajdCmd, String)"));

    static String kljuc(Method m) {
        return m.getName() + Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    static Set<String> javneMetode(Class<?> fasada) {
        return Arrays.stream(fasada.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
                .map(McpFasadeTest::kljuc)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void javneMetodeFasadaSuZakucane() {
        for (var stavka : METODE.entrySet()) {
            assertEquals(new TreeSet<>(stavka.getValue()), javneMetode(stavka.getKey()),
                    "javne metode fasade " + stavka.getKey().getSimpleName());
        }
    }

    @Test
    void fasadeSuServisiUPaketuServis() {
        for (Class<?> fasada : METODE.keySet()) {
            assertEquals("tri.novica.gfssystem.mcp.servis", fasada.getPackageName());
            assertTrue(fasada.isAnnotationPresent(org.springframework.stereotype.Service.class), fasada.getSimpleName());
        }
    }
}
