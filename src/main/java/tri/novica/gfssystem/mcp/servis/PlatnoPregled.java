package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.dto.uzivo.platno.Animacija;
import tri.novica.gfssystem.dto.uzivo.platno.Celija;
import tri.novica.gfssystem.dto.uzivo.platno.CrtezSloj;
import tri.novica.gfssystem.dto.uzivo.platno.Deo;
import tri.novica.gfssystem.dto.uzivo.platno.FormulaSloj;
import tri.novica.gfssystem.dto.uzivo.platno.OblikSloj;
import tri.novica.gfssystem.dto.uzivo.platno.Okidac;
import tri.novica.gfssystem.dto.uzivo.platno.Pasus;
import tri.novica.gfssystem.dto.uzivo.platno.Platno;
import tri.novica.gfssystem.dto.uzivo.platno.Potez;
import tri.novica.gfssystem.dto.uzivo.platno.SlikaSloj;
import tri.novica.gfssystem.dto.uzivo.platno.Sloj;
import tri.novica.gfssystem.dto.uzivo.platno.TabelaSloj;
import tri.novica.gfssystem.dto.uzivo.platno.TekstSloj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * PLATNO dokument za Kloda (spec MCP 4.4), čiste funkcije nad {@link Platno} record-om (ne kroz {@code PlatnoJson}, koji
 * je bean): sažetak slojeva u z-redu dokumenta ({@link #slojevi}), dokument bez poteza crteža ({@link #bezPoteza}) i broj
 * tačaka po CRTEZ sloju ({@link #brojTacaka}). Potezi su veliki (do 20 000 tačaka po crtežu) i modelu ne trebaju.
 * <p>
 * Tekst sloja (TEKST, ćelija TABELA, FORMULA {@code latex}) je skraćen na {@value #MAX_TEKST_SLOJA} znakova (kodnih tačaka)
 * sa {@code …} na kraju ({@link #skrati}); ceo tekst daje alat {@code slajd}.
 */
final class PlatnoPregled {

    static final int MAX_TEKST_SLOJA = 1000;
    /** Znak na kraju skraćenog teksta. */
    static final String SKRACENO = "…";

    private PlatnoPregled() {
    }

    /**
     * Sažetak svakog sloja redom dokumenta (prvi je najniži): id, tip, naziv, okvir {@code [x, y, sirina, visina]}, pa po
     * tipu: TEKST pasusi spojeni sa {@code \n} (delovi nadovezani) i preovlađujući font i veličina (po broju znakova; kod
     * jednakog broja prvi po redu), TABELA mreža teksta ćelija (pasusi ćelije spojeni sa {@code \n}), FORMULA {@code latex},
     * SLIKA i CRTEZ {@code medijId}, CRTEZ broj tačaka, OBLIK vrsta oblika. Bez dokumenta ili slojeva prazna lista.
     */
    static List<McpSlojSazetak> slojevi(Platno p) {
        if (p == null || p.slojevi() == null) {
            return List.of();
        }
        return p.slojevi().stream().map(PlatnoPregled::sloj).toList();
    }

    /** Dokument u kome svaki CRTEZ sloj ima {@code potezi = null}; ostalo isto. {@code null} ostaje {@code null}. */
    static Platno bezPoteza(Platno p) {
        return p == null ? null : p.bezPoteza();
    }

    /** Id CRTEZ sloja -> broj tačaka svih poteza ({@code tacke.size() / 2}), redom slojeva; bez dokumenta prazno. */
    static Map<String, Integer> brojTacaka(Platno p) {
        Map<String, Integer> broj = new LinkedHashMap<>();
        if (p != null && p.slojevi() != null) {
            for (Sloj s : p.slojevi()) {
                if (s instanceof CrtezSloj c) {
                    broj.put(c.id(), brojTacaka(c));
                }
            }
        }
        return Collections.unmodifiableMap(broj);
    }

    /** Koraci PLATNO slajda: broj animacija sa okidačem KLIK (kao {@code PlatnoJson.brojKoraka}); bez dokumenta 0. */
    static int brojKoraka(Platno p) {
        if (p == null || p.animacije() == null) {
            return 0;
        }
        int n = 0;
        for (Animacija a : p.animacije()) {
            if (a != null && a.okidac() == Okidac.KLIK) {
                n++;
            }
        }
        return n;
    }

    /**
     * Najviše {@code max} kodnih tačaka: duži tekst postaje prvih {@code max - 1} i {@value #SKRACENO}. {@code null} ostaje
     * {@code null}.
     */
    static String skrati(String tekst, int max) {
        if (tekst == null || tekst.codePointCount(0, tekst.length()) <= max) {
            return tekst;
        }
        return tekst.substring(0, tekst.offsetByCodePoints(0, max - 1)) + SKRACENO;
    }

    private static McpSlojSazetak sloj(Sloj s) {
        double[] okvir = {broj(s.x()), broj(s.y()), broj(s.sirina()), broj(s.visina())};
        return switch (s) {
            case TekstSloj t -> {
                List<Deo> delovi = delovi(t.pasusi());
                yield new McpSlojSazetak(t.id(), "TEKST", t.naziv(), okvir, skrati(tekst(t.pasusi()), MAX_TEKST_SLOJA),
                        preovladjuje(delovi, Deo::font), preovladjuje(delovi, Deo::velicina), null, null, null, null, null);
            }
            case SlikaSloj sl -> new McpSlojSazetak(sl.id(), "SLIKA", sl.naziv(), okvir, null, null, null, null, null,
                    sl.medijId(), null, null);
            case OblikSloj o -> new McpSlojSazetak(o.id(), "OBLIK", o.naziv(), okvir, null, null, null, null, null, null,
                    null, o.oblik() == null ? null : o.oblik().name());
            case TabelaSloj tb -> new McpSlojSazetak(tb.id(), "TABELA", tb.naziv(), okvir, null, null, null,
                    tabela(tb.celije()), null, null, null, null);
            case FormulaSloj f -> new McpSlojSazetak(f.id(), "FORMULA", f.naziv(), okvir, null, null, null, null,
                    skrati(f.latex(), MAX_TEKST_SLOJA), null, null, null);
            case CrtezSloj c -> new McpSlojSazetak(c.id(), "CRTEZ", c.naziv(), okvir, null, null, null, null, null,
                    c.medijId(), brojTacaka(c), null);
        };
    }

    private static int brojTacaka(CrtezSloj c) {
        int n = 0;
        if (c.potezi() != null) {
            for (Potez potez : c.potezi()) {
                if (potez != null && potez.tacke() != null) {
                    n += potez.tacke().size() / 2;
                }
            }
        }
        return n;
    }

    /** Pasusi spojeni sa {@code \n}, delovi pasusa nadovezani. */
    private static String tekst(List<Pasus> pasusi) {
        if (pasusi == null) {
            return "";
        }
        List<String> redovi = new ArrayList<>();
        for (Pasus p : pasusi) {
            StringBuilder red = new StringBuilder();
            if (p != null && p.delovi() != null) {
                for (Deo d : p.delovi()) {
                    if (d != null && d.tekst() != null) {
                        red.append(d.tekst());
                    }
                }
            }
            redovi.add(red.toString());
        }
        return String.join("\n", redovi);
    }

    private static List<List<String>> tabela(List<List<Celija>> celije) {
        if (celije == null) {
            return List.of();
        }
        List<List<String>> mreza = new ArrayList<>();
        for (List<Celija> red : celije) {
            List<String> tekstovi = new ArrayList<>();
            if (red != null) {
                for (Celija c : red) {
                    tekstovi.add(skrati(c == null ? "" : tekst(c.pasusi()), MAX_TEKST_SLOJA));
                }
            }
            mreza.add(List.copyOf(tekstovi));
        }
        return List.copyOf(mreza);
    }

    private static List<Deo> delovi(List<Pasus> pasusi) {
        List<Deo> delovi = new ArrayList<>();
        if (pasusi != null) {
            for (Pasus p : pasusi) {
                if (p != null && p.delovi() != null) {
                    p.delovi().stream().filter(Objects::nonNull).forEach(delovi::add);
                }
            }
        }
        return delovi;
    }

    /** Vrednost svojstva sa najviše znakova teksta; kod jednakog broja prva po redu, bez delova {@code null}. */
    private static <T> T preovladjuje(List<Deo> delovi, Function<Deo, T> svojstvo) {
        Map<T, Integer> znakova = new LinkedHashMap<>();
        for (Deo d : delovi) {
            T v = svojstvo.apply(d);
            if (v != null) {
                String t = d.tekst() == null ? "" : d.tekst();
                znakova.merge(v, t.codePointCount(0, t.length()), Integer::sum);
            }
        }
        T najvise = null;
        int max = -1;
        for (Map.Entry<T, Integer> e : znakova.entrySet()) {
            if (e.getValue() > max) {
                najvise = e.getKey();
                max = e.getValue();
            }
        }
        return najvise;
    }

    private static double broj(Double v) {
        return v == null ? 0 : v;
    }
}
