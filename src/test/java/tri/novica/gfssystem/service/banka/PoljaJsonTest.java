package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoljaJsonTest {

    private static final List<PoljeOpcija> OPCIJE = List.of(new PoljeOpcija(1L, "A"), new PoljeOpcija(2L, "B $q_0$"));

    private static List<Polje> svaPolja() {
        return List.of(
                new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, 0.5, OdstupanjeTip.PROCENAT, "cm", null, null, null),
                new Polje("b", "b) heks u binarni", TipPolja.TEKST, null, null, null, true, false, null),
                new Polje("c", "Koji automat prihvata abba?", TipPolja.JEDAN_TACAN, null, null, null, null, null, OPCIJE),
                new Polje("d", "Označi sve tačne", TipPolja.VISE_TACNIH, null, null, null, null, null, OPCIJE));
    }

    private static Map<String, OdgovorPolja> sviOdgovori() {
        Map<String, OdgovorPolja> m = new LinkedHashMap<>();
        m.put("a", new OdgovorPolja(45.0, null, null, null));
        m.put("b", new OdgovorPolja(null, List.of("10110110", "1011 0110"), null, null));
        m.put("c", new OdgovorPolja(null, null, 2L, null));
        m.put("d", new OdgovorPolja(null, null, null, List.of(1L, 3L)));
        return m;
    }

    private static void odbija(Runnable r) {
        assertThatThrownBy(r::run)
                .isInstanceOfSatisfying(SystemException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("Neispravan format podataka.");
                });
    }

    @Test
    void okruglPutPolja() {
        List<Polje> polja = svaPolja();
        String json = PoljaJson.uJson(polja);
        assertThat(PoljaJson.polja(json)).isEqualTo(polja);
        assertThat(PoljaJson.izKolone(json)).isEqualTo(polja);
    }

    @Test
    void okruglPutOdgovora() {
        Map<String, OdgovorPolja> odgovori = sviOdgovori();
        String json = PoljaJson.uJson(odgovori);
        Map<String, OdgovorPolja> nazad = PoljaJson.odgovori(json);
        assertThat(nazad).isEqualTo(odgovori);
        // redosled ključeva se čuva
        assertThat(nazad.keySet()).containsExactly("a", "b", "c", "d");
        assertThat(PoljaJson.odgovoriIzKolone(json)).isEqualTo(odgovori);
    }

    @Test
    void upisujeSamoPostavljenaPolja() {
        assertThat(PoljaJson.uJson(Map.of("a", new OdgovorPolja(45.0, null, null, null))))
                .isEqualTo("{\"a\":{\"broj\":45.0}}");
        assertThat(PoljaJson.uJson(List.of(new Polje("a", "x", TipPolja.TEKST, null, null, null, null, null, null))))
                .isEqualTo("[{\"kljuc\":\"a\",\"oznaka\":\"x\",\"tip\":\"TEKST\"}]");
    }

    @Test
    void prazanSkupJeValidanJson() {
        assertThat(PoljaJson.uJson(List.of())).isEqualTo("[]");
        assertThat(PoljaJson.uJson(Map.of())).isEqualTo("{}");
        assertThat(PoljaJson.polja("[]")).isEmpty();
        assertThat(PoljaJson.odgovori("{}")).isEmpty();
    }

    @Test
    void nepoznatoPoljeJe400() {
        odbija(() -> PoljaJson.polja("[{\"kljuc\":\"a\",\"x\":1}]"));
        odbija(() -> PoljaJson.polja("[{\"kljuc\":\"a\",\"opcije\":[{\"id\":1,\"tekst\":\"A\",\"tacna\":true}]}]"));
        odbija(() -> PoljaJson.odgovori("{\"a\":{\"broj\":1,\"x\":2}}"));
    }

    @Test
    void pogresanTipJe400() {
        odbija(() -> PoljaJson.polja("[{\"kljuc\":1}]"));
        odbija(() -> PoljaJson.polja("[{\"kljuc\":\"a\",\"tip\":\"NEPOZNAT\"}]"));
        odbija(() -> PoljaJson.polja("[{\"kljuc\":\"a\",\"tip\":0}]"));
        odbija(() -> PoljaJson.polja("[{\"kljuc\":\"a\",\"odstupanje\":\"1\"}]"));
        odbija(() -> PoljaJson.polja("[{\"kljuc\":\"a\",\"zanemariRazmake\":\"true\"}]"));
        odbija(() -> PoljaJson.polja("[{\"kljuc\":\"a\",\"opcije\":[{\"id\":1.5,\"tekst\":\"A\"}]}]"));
        odbija(() -> PoljaJson.odgovori("{\"a\":{\"tacna\":2.5}}"));
        odbija(() -> PoljaJson.odgovori("{\"a\":{\"tacna\":2.0}}"));
        odbija(() -> PoljaJson.odgovori("{\"a\":{\"broj\":\"45\"}}"));
        odbija(() -> PoljaJson.odgovori("{\"a\":{\"tacne\":[1,\"2\"]}}"));
        odbija(() -> PoljaJson.odgovori("{\"a\":{\"prihvatljivi\":[1]}}"));
        // lista umesto objekta i obrnuto
        odbija(() -> PoljaJson.polja("{}"));
        odbija(() -> PoljaJson.odgovori("[]"));
    }

    @Test
    void visakTokenaIPokvarenJsonJe400() {
        odbija(() -> PoljaJson.polja("[] []"));
        odbija(() -> PoljaJson.polja("[] x"));
        odbija(() -> PoljaJson.odgovori("{} {}"));
        odbija(() -> PoljaJson.polja("[{\"kljuc\":\"a\""));
        odbija(() -> PoljaJson.odgovori("{\"a\":"));
    }

    @Test
    void nedostajuciUlazJe400() {
        odbija(() -> PoljaJson.polja(null));
        odbija(() -> PoljaJson.polja("  "));
        odbija(() -> PoljaJson.polja("null"));
        odbija(() -> PoljaJson.odgovori(null));
        odbija(() -> PoljaJson.odgovori("null"));
        odbija(() -> PoljaJson.polja("[null]"));
        odbija(() -> PoljaJson.odgovori("{\"a\":null}"));
    }

    @Test
    void dupliKljucUOdgovorimaJe400() {
        odbija(() -> PoljaJson.odgovori("{\"a\":{\"broj\":1},\"a\":{\"broj\":2}}"));
    }

    @Test
    void izKolonePrihvataNepoznataPolja() {
        List<Polje> polja = PoljaJson.izKolone("[{\"kljuc\":\"a\",\"oznaka\":\"x\",\"tip\":\"BROJ\",\"novo\":{\"z\":1}}]");
        assertThat(polja).containsExactly(
                new Polje("a", "x", TipPolja.BROJ, null, null, null, null, null, null));

        Map<String, OdgovorPolja> odgovori = PoljaJson.odgovoriIzKolone("{\"a\":{\"broj\":45,\"jedinicaOdgovora\":\"cm\"}}");
        assertThat(odgovori).containsOnlyKeys("a");
        assertThat(odgovori.get("a").broj()).isEqualTo(45.0);
    }

    @Test
    void izKoloneNullIPrazanSuPrazni() {
        assertThat(PoljaJson.izKolone(null)).isEmpty();
        assertThat(PoljaJson.izKolone(" ")).isEmpty();
        assertThat(PoljaJson.odgovoriIzKolone(null)).isEmpty();
        assertThat(PoljaJson.odgovoriIzKolone("")).isEmpty();
    }

    @Test
    void izKoloneOstajeStrogaZaTipoveIOblik() {
        odbija(() -> PoljaJson.izKolone("[{\"kljuc\":1}]"));
        odbija(() -> PoljaJson.izKolone("[] []"));
        odbija(() -> PoljaJson.odgovoriIzKolone("{\"a\":{\"tacna\":2.5}}"));
    }
}
