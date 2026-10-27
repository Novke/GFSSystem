package tri.novica.gfssystem.utility;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Smer studija: prvi deo indeksa ({@code GD5} = geodezija, broj 5). Javne forme (nalog, upis) nude samo ove smerove; za sada
 * je jedini geodezija, pa je {@code GD} u formi zaključan. Nov smer = nova konstanta ovde i isti red u frontu
 * ({@code shared/util/indeks.ts}, {@code SMEROVI}); tabela u bazi nije potrebna jer se smer ne čuva odvojeno od indeksa.
 * Šifra je 1-4 velika latinična slova (kanonski oblik indeksa je {@code <šifra><broj>}, {@link IndeksUtil}).
 */
public enum Smer {
    GEODEZIJA("GD", "Geodezija");

    private final String sifra;
    private final String naziv;

    Smer(String sifra, String naziv) {
        this.sifra = sifra;
        this.naziv = naziv;
    }

    /** Šifra u indeksu ({@code "GD"}). */
    public String sifra() {
        return sifra;
    }

    /** Naziv za prikaz ({@code "Geodezija"}). */
    public String naziv() {
        return naziv;
    }

    /** Šifre svih smerova, redom kao u enumu. */
    public static List<String> sifre() {
        return Arrays.stream(values()).map(Smer::sifra).toList();
    }

    /** Smer po šifri (tačno poklapanje, velika slova); prazno za nepoznatu ili null. */
    public static Optional<Smer> poSifri(String sifra) {
        return Arrays.stream(values()).filter(s -> s.sifra.equals(sifra)).findFirst();
    }
}
