package tri.novica.gfssystem.service.uzivo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.platno.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabrika ispravnih dokumenata platna za testove (B3, B4). Dokumenti su već u normalizovanom obliku (sva polja
 * popunjena), pa {@code PlatnoPP.normalizuj} ne menja ništa.
 */
public final class PlatnoTestPodaci {

    public static final String SLIKA = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";
    public static final String CRTEZ = "7a9d0c11-2b34-4f56-8a78-9b0c1d2e3f40";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private PlatnoTestPodaci() {}

    /** Svi slojevi redom: t1 TEKST (2 pasusa), s1 SLIKA, o1 OBLIK, l1 LINIJA, tb TABELA 2x2, f1 FORMULA, c1 CRTEZ. */
    public static Platno ispravno() {
        return ispravno(SLIKA, CRTEZ);
    }

    public static Platno ispravno(String slikaId, String crtezId) {
        List<Sloj> slojevi = List.of(
                tekst("t1"),
                new SlikaSloj("s1", null, 100.0, 100.0, 200.0, 150.0, 0.0, 1.0, false, null, slikaId,
                        new Isecak(0.0, 0.0, 0.0, 0.0)),
                new OblikSloj("o1", "Okvir", 10.0, 10.0, 300.0, 100.0, 0.0, 1.0, false, null,
                        VrstaOblika.PRAVOUGAONIK, "#FFEEAA", ivica(), null, null),
                new OblikSloj("l1", null, 50.0, 400.0, 300.0, 60.0, 0.0, 1.0, false, null,
                        VrstaOblika.STRELICA, null, ivica(), new Tacka(0.0, 30.0), new Tacka(300.0, 30.0)),
                tabela("tb"),
                new FormulaSloj("f1", null, 500.0, 300.0, 200.0, 60.0, 0.0, 1.0, false, null,
                        "x^2+y^2=r^2", "#000000", 24.0, Poravnanje.CENTAR, null),
                new CrtezSloj("c1", null, 0.0, 0.0, 960.0, 540.0, 0.0, 1.0, false, null, crtezId,
                        List.of(new Potez("#FF0000", 2.0, List.of(0.0, 0.0, 10.0, 10.0, 20.0, 5.0)))));
        List<Animacija> animacije = List.of(
                new Animacija("a1", "t1", 0, VrstaAnimacije.ULAZ, Efekat.POJAVI, null, Okidac.KLIK, 0, 0),
                new Animacija("a2", "s1", null, VrstaAnimacije.ULAZ, Efekat.FADE, null, Okidac.SA_PRETHODNIM, 500, 0),
                new Animacija("a3", "o1", null, VrstaAnimacije.ULAZ, Efekat.ULET, Smer.DOLE, Okidac.POSLE_PRETHODNOG, 500, 100),
                new Animacija("a4", "t1", null, VrstaAnimacije.IZLAZ, Efekat.ZUM, null, Okidac.KLIK, 500, 0));
        return new Platno(1, new Pozadina("#FFFFFF", null), slojevi, animacije);
    }

    /** Jedan tekstualni sloj i {@code klikova} animacija sa okidačem KLIK (za korake izvođenja). */
    public static Platno sKlikovima(int klikova) {
        List<Animacija> animacije = new ArrayList<>();
        for (int i = 0; i < klikova; i++) {
            animacije.add(new Animacija("k" + i, "t1", null, VrstaAnimacije.ULAZ, Efekat.FADE, null, Okidac.KLIK, 500, 0));
        }
        return new Platno(1, new Pozadina("#FFFFFF", null), List.of(tekst("t1")), animacije);
    }

    public static TekstSloj tekst(String id) {
        return new TekstSloj(id, "Naslov", 40.0, 30.0, 880.0, 120.0, 0.0, 1.0, false, null,
                List.of(pasus("Prvi pasus"), pasus("Drugi pasus")), new Okvir(7.2, 3.6, 7.2, 3.6, Sidro.VRH, true, 1.0, 1.0),
                null, null);
    }

    public static Pasus pasus(String tekst) {
        return new Pasus(Poravnanje.LEVO, 0, null, new Uvlacenje(0.0, 0.0), 0.0, 0.0, 100.0, List.of(),
                List.of(new Deo(tekst, "Carlito", 18.0, "#000000", false, false, false, false, Polozaj.OBICAN)));
    }

    public static Ivica ivica() {
        return new Ivica("#333333", 1.0, Crtica.PUNA);
    }

    private static TabelaSloj tabela(String id) {
        List<List<Celija>> celije = List.of(
                List.of(celija("A"), celija("B")),
                List.of(celija("C"), celija("D")));
        return new TabelaSloj(id, null, 100.0, 250.0, 300.0, 100.0, 0.0, 1.0, false, null,
                List.of(150.0, 150.0), List.of(50.0, 50.0), celije);
    }

    private static Celija celija(String tekst) {
        return new Celija(List.of(pasus(tekst)), null, new IviceCelije(null, null, null, null), 0, 0, false, Sidro.VRH);
    }

    /** Dokument kao sirovi JSON, kakav stiže od klijenta. */
    public static JsonNode cvor(Platno p) {
        return MAPPER.readTree(new PlatnoJson().zapisi(p));
    }
}
