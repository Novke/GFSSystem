package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.ElementCrteza;
import tri.novica.gfssystem.dto.uzivo.TipElementa;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Granice crteža (spec crtanja 4.1) i strogo čitanje tela, bez baze. */
class CrtanjePravilaTest {

    final CrtanjeJson json = new CrtanjeJson();
    final JsonMapper mapper = JsonMapper.builder().build();

    static ElementCrteza el(TipElementa tip, String boja, double debljina, Double... tacke) {
        return new ElementCrteza(tip, boja, debljina, List.of(tacke));
    }

    static void los(List<ElementCrteza> el) {
        SystemException e = assertThrows(SystemException.class, () -> CrtanjePravila.normalizuj(el));
        assertTrue(e.getMessage().startsWith("Neispravan crtež"), e.getMessage());
    }

    @Test
    void ispravnoSeNormalizuje() {
        List<ElementCrteza> r = CrtanjePravila.normalizuj(List.of(
                el(TipElementa.OLOVKA, "#c62828", 6, 1.04, 2.06),
                el(TipElementa.STRELICA, "#2456A6", 3, 0.0, 0.0, 959.99, -12.34)));
        assertEquals("#C62828", r.get(0).boja());
        assertEquals(List.of(1.0, 2.1), r.get(0).tacke());
        assertEquals(List.of(0.0, 0.0, 960.0, -12.3), r.get(1).tacke());
    }

    @Test
    void granice() {
        los(null);
        los(Collections.singletonList(null));
        los(List.of(new ElementCrteza(null, "#000000", 3.0, List.of(1.0, 1.0))));
        los(List.of(el(TipElementa.LINIJA, "red", 3, 0.0, 0.0, 1.0, 1.0)));
        los(List.of(el(TipElementa.LINIJA, "#00000", 3, 0.0, 0.0, 1.0, 1.0)));
        los(List.of(el(TipElementa.LINIJA, "#000000", 0.5, 0.0, 0.0, 1.0, 1.0)));
        los(List.of(el(TipElementa.LINIJA, "#000000", 41, 0.0, 0.0, 1.0, 1.0)));
        los(List.of(new ElementCrteza(TipElementa.LINIJA, "#000000", Double.NaN, List.of(0.0, 0.0, 1.0, 1.0))));
        los(List.of(el(TipElementa.PRAVOUGAONIK, "#000000", 3, 0.0, 0.0, 1.0)));
        los(List.of(el(TipElementa.KRUG, "#000000", 3, 0.0, 0.0, 1.0, 1.0, 2.0, 2.0)));
        los(List.of(el(TipElementa.OLOVKA, "#000000", 3, 1.0)));
        los(List.of(el(TipElementa.OLOVKA, "#000000", 3)));
        los(List.of(el(TipElementa.ELIPSA, "#000000", 3, 0.0, 0.0, 2000.1, 1.0)));
        los(List.of(new ElementCrteza(TipElementa.ELIPSA, "#000000", 3.0, java.util.Arrays.asList(0.0, null, 1.0, 1.0))));
        los(List.of(new ElementCrteza(TipElementa.ELIPSA, "#000000", 3.0, List.of(0.0, Double.POSITIVE_INFINITY, 1.0, 1.0))));

        List<Double> pet = new ArrayList<>();
        for (int i = 0; i < 2 * CrtanjePravila.MAX_TACAKA_OLOVKE; i++) pet.add((double) (i % 900));
        assertEquals(1, CrtanjePravila.normalizuj(List.of(new ElementCrteza(TipElementa.OLOVKA, "#000000", 3.0, pet))).size());
        List<Double> previse = new ArrayList<>(pet);
        previse.add(1.0);
        previse.add(1.0);
        los(List.of(new ElementCrteza(TipElementa.OLOVKA, "#000000", 3.0, previse)));

        List<ElementCrteza> mnogo = new ArrayList<>();
        for (int i = 0; i <= CrtanjePravila.MAX_ELEMENATA; i++) mnogo.add(el(TipElementa.LINIJA, "#000000", 3, 0.0, 0.0, 1.0, 1.0));
        los(mnogo);
        assertEquals(CrtanjePravila.MAX_ELEMENATA, CrtanjePravila.normalizuj(mnogo.subList(0, CrtanjePravila.MAX_ELEMENATA)).size());
    }

    @Test
    void velicina() {
        assertDoesNotThrow(() -> CrtanjePravila.proveriVelicinu("x".repeat(CrtanjePravila.MAX_BAJTOVA)));
        SystemException e = assertThrows(SystemException.class,
                () -> CrtanjePravila.proveriVelicinu("x".repeat(CrtanjePravila.MAX_BAJTOVA + 1)));
        assertEquals(400, e.getCode());
    }

    @Test
    void strogoCitanje() {
        assertEquals(1, json.procitaj(mapper.readTree(
                "{\"elementi\":[{\"tip\":\"KRUG\",\"boja\":\"#000000\",\"debljina\":6,\"tacke\":[1,2,3,4]}]}")).elementi().size());
        for (String telo : List.of(
                "null",
                "{\"elementi\":[{\"tip\":\"KRUG\",\"boja\":\"#000000\",\"debljina\":6,\"tacke\":[1,2,3,4],\"x\":1}]}",
                "{\"elementi\":[],\"visak\":true}",
                "{\"elementi\":[{\"tip\":\"ZVEZDA\",\"boja\":\"#000000\",\"debljina\":6,\"tacke\":[1,2,3,4]}]}",
                "{\"elementi\":[{\"tip\":0,\"boja\":\"#000000\",\"debljina\":6,\"tacke\":[1,2,3,4]}]}",
                "{\"elementi\":[{\"tip\":\"KRUG\",\"boja\":123456,\"debljina\":6,\"tacke\":[1,2,3,4]}]}",
                "{\"elementi\":[{\"tip\":\"KRUG\",\"boja\":\"#000000\",\"debljina\":\"6\",\"tacke\":[1,2,3,4]}]}")) {
            SystemException e = assertThrows(SystemException.class, () -> json.procitaj(mapper.readTree(telo)), telo);
            assertEquals(400, e.getCode(), telo);
        }
    }
}
