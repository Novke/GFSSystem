package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.platno.*;
import tri.novica.gfssystem.exceptions.SystemException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatnoJsonTest {

    private final PlatnoJson json = new PlatnoJson();

    private static JsonNode cvor(String s) {
        return JsonMapper.builder().build().readTree(s);
    }

    private static final String OSNOVA =
            "\"id\":\"%s\",\"naziv\":null,\"x\":10,\"y\":20.5,\"sirina\":100,\"visina\":50,\"rotacija\":0,"
                    + "\"providnost\":1,\"zakljucan\":false,\"senka\":null";

    private static String osnova(String id) {
        return OSNOVA.formatted(id);
    }

    private static final String PASUS = "{\"poravnanje\":\"LEVO\",\"nivo\":0,\"lista\":null,"
            + "\"uvlacenje\":{\"levo\":0,\"prviRed\":0},\"razmakPre\":0,\"razmakPosle\":0,\"proredProcenat\":100,"
            + "\"tabulatori\":[{\"poz\":10,\"poravnanje\":\"DECIMALNO\"}],\"delovi\":[{\"tekst\":\"Zdravo\","
            + "\"font\":\"Carlito\",\"velicina\":18,\"boja\":\"#000000\",\"podebljano\":false,\"kurziv\":false,"
            + "\"podvuceno\":false,\"precrtano\":false,\"polozaj\":\"OBICAN\"}]}";

    private static final String IVICA = "{\"boja\":\"#FF0000\",\"debljina\":1,\"crtica\":\"PUNA\"}";

    private static String slojevi() {
        return "{" + osnova("t1") + ",\"tip\":\"TEKST\",\"pasusi\":[" + PASUS + "],\"okvir\":{\"unutraLevo\":7.2,"
                + "\"unutraGore\":3.6,\"unutraDesno\":7.2,\"unutraDole\":3.6,\"sidro\":\"VRH\",\"prelom\":true,"
                + "\"skala\":1,\"skalaProreda\":1},\"popuna\":null,\"ivica\":null},"
                + "{" + osnova("s1") + ",\"tip\":\"SLIKA\",\"medijId\":\"m1\",\"isecak\":{\"levo\":0,\"gore\":0,"
                + "\"desno\":0,\"dole\":0}},"
                + "{" + osnova("o1") + ",\"tip\":\"OBLIK\",\"oblik\":\"LINIJA\",\"popuna\":null,\"ivica\":" + IVICA
                + ",\"od\":{\"x\":0,\"y\":25},\"do\":{\"x\":100,\"y\":25}},"
                + "{" + osnova("b1") + ",\"tip\":\"TABELA\",\"kolone\":[50,50],\"redovi\":[25],\"celije\":[[{"
                + "\"pasusi\":[" + PASUS + "],\"popuna\":null,\"ivice\":{\"gore\":null,\"dole\":" + IVICA
                + ",\"levo\":null,\"desno\":null},\"spojDesno\":1,\"spojDole\":0,\"pokrivena\":false,"
                + "\"sidro\":\"VRH\"}]]},"
                + "{" + osnova("f1") + ",\"tip\":\"FORMULA\",\"latex\":\"x^2\",\"boja\":\"#000000\",\"velicina\":18,"
                + "\"poravnanje\":\"CENTAR\",\"slikaIzvora\":null},"
                + "{" + osnova("c1") + ",\"tip\":\"CRTEZ\",\"medijId\":\"m2\",\"potezi\":[{\"boja\":\"#00FF00\","
                + "\"debljina\":2,\"tacke\":[1,2,3.5,4]}]}";
    }

    private static String animacija(String okidac, String efekat, String trajanje) {
        return "{\"id\":\"a\",\"slojId\":\"t1\",\"pasus\":null,\"vrsta\":\"ULAZ\",\"efekat\":\"" + efekat
                + "\",\"smer\":null,\"okidac\":\"" + okidac + "\",\"trajanjeMs\":" + trajanje + ",\"kasnjenjeMs\":0}";
    }

    private static String dokument(String slojevi, String animacije, String visak) {
        return "{\"verzija\":1,\"pozadina\":{\"boja\":\"#FFFFFF\",\"medijId\":null},\"slojevi\":[" + slojevi
                + "],\"animacije\":[" + animacije + "]" + visak + "}";
    }

    private static String pun() {
        return dokument(slojevi(), animacija("KLIK", "FADE", "500"), "");
    }

    private void odbijeno(String s) {
        assertThatThrownBy(() -> json.procitaj(cvor(s))).isInstanceOf(SystemException.class)
                .hasMessageStartingWith("Neispravan dokument platna: ")
                .extracting("code").isEqualTo(400);
    }

    @Test
    void citaSveVrsteSlojeva() {
        Platno p = json.procitaj(cvor(pun()));
        assertThat(p.slojevi()).hasSize(6);
        assertThat(p.slojevi().get(0)).isInstanceOf(TekstSloj.class);
        assertThat(p.slojevi().get(1)).isInstanceOf(SlikaSloj.class);
        assertThat(p.slojevi().get(2)).isInstanceOf(OblikSloj.class);
        assertThat(p.slojevi().get(3)).isInstanceOf(TabelaSloj.class);
        assertThat(p.slojevi().get(4)).isInstanceOf(FormulaSloj.class);
        assertThat(p.slojevi().get(5)).isInstanceOf(CrtezSloj.class);
        OblikSloj o = (OblikSloj) p.slojevi().get(2);
        assertThat(o.doTacke()).isNotNull();
        assertThat(o.doTacke().x()).isEqualTo(100.0);
        assertThat(p.animacije()).hasSize(1);
        assertThat(p.animacije().get(0).efekat()).isEqualTo(Efekat.FADE);
    }

    @Test
    void nepoznatoPoljeJe400() {
        odbijeno(dokument("", "", ",\"visak\":1"));
    }

    @Test
    void nepoznatoPoljeUSlojuJe400() {
        odbijeno(dokument("{" + osnova("s1") + ",\"tip\":\"SLIKA\",\"medijId\":\"m\",\"isecak\":{\"levo\":0,"
                + "\"gore\":0,\"desno\":0,\"dole\":0},\"visak\":true}", "", ""));
    }

    @Test
    void nepoznatTipSlojaJe400() {
        odbijeno(dokument("{" + osnova("v1") + ",\"tip\":\"VIDEO\"}", "", ""));
    }

    @Test
    void stringUmestoBrojaJe400() {
        odbijeno(pun().replace("\"x\":10,", "\"x\":\"12\","));
    }

    @Test
    void decimalaZaCeoBrojJe400() {
        odbijeno(dokument("", animacija("KLIK", "FADE", "500.5"), ""));
    }

    @Test
    void nepoznataVrednostEnumaJe400() {
        odbijeno(dokument("", animacija("KLIK", "SPIN", "500"), ""));
    }

    @Test
    void visakNaKrajuJe400() {
        assertThatThrownBy(() -> json.izKolone(pun() + " {}")).isInstanceOf(SystemException.class);
    }

    @Test
    void porukaNeNosiCeoUlaz() {
        String dug = "x".repeat(5000);
        assertThatThrownBy(() -> json.procitaj(cvor(pun().replace("\"x\":10,", "\"x\":\"" + dug + "\","))))
                .hasMessageMatching("(?s).{0,260}");
    }

    @Test
    void zapisiPaProcitajDajeIsto() {
        Platno p = json.procitaj(cvor(pun()));
        String s = json.zapisi(p);
        assertThat(json.izKolone(s)).isEqualTo(p);
        assertThat(json.izKolone(null)).isNull();
        assertThat(json.velicinaBajtova(p)).isEqualTo(s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertThat(s).contains("\"tip\":\"TEKST\"").contains("\"do\":{");
    }

    @Test
    void bezPotezaBriseSamoPoteze() {
        Platno p = json.procitaj(cvor(pun()));
        Platno b = p.bezPoteza();
        CrtezSloj orig = (CrtezSloj) p.slojevi().get(5);
        CrtezSloj nov = (CrtezSloj) b.slojevi().get(5);
        assertThat(orig.potezi()).isNotNull();
        assertThat(nov.potezi()).isNull();
        assertThat(nov.medijId()).isEqualTo(orig.medijId());
        assertThat(b.slojevi().subList(0, 5)).isEqualTo(p.slojevi().subList(0, 5));
        assertThat(b.animacije()).isEqualTo(p.animacije());
    }

    @Test
    void brojKorakaBrojiKlik() {
        String s = dokument("", animacija("KLIK", "FADE", "500") + "," + animacija("SA_PRETHODNIM", "FADE", "500")
                + "," + animacija("KLIK", "POJAVI", "0"), "");
        assertThat(PlatnoJson.brojKoraka(s)).isEqualTo(2);
        assertThat(PlatnoJson.brojKoraka(null)).isZero();
        assertThat(PlatnoJson.brojKoraka("")).isZero();
    }

    @Test
    void aplikacioniMapperSerijalizujeTip() {
        Platno p = json.procitaj(cvor(pun()));
        String s = JsonMapper.builder().build().writeValueAsString(p);
        assertThat(s).contains("\"tip\":\"TEKST\"").contains("\"tip\":\"CRTEZ\"");
    }
}
