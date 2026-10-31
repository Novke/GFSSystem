package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.Polje;

import java.util.List;

/**
 * Zadatak slajda sa opisom čvora banke (izlaz; editor, konzola i MCP). Prva četiri polja su ono što je na slajdu, ostalo
 * se računa iz banke u trenutku čitanja ({@code ZadaciSlajdova}): {@code putanja} od korena do grupe, {@code list} (grupa
 * nema podgrupa), {@code brojAktivnihVarijanti} = kandidati nivoa slajda (AKTIVNE sa SVG-om u listovima koji se izvlače, bez
 * isključenih podgrupa; {@code nivo == null} = svih nivoa), {@code brojAktivnihPoNivou} = isti kandidati po nivou (tačno tri
 * broja, za nivo 1, 2 i 3; konzola iz njih računa broj za izmenu nivoa), {@code polja} = šema za list ({@code null} za
 * unutrašnji čvor, prazna lista = rad na papiru), {@code iskljucena} = čvor ili neki njegov predak ima isključenu temu, {@code iskljucenePodgrupe} se ne izvlače. {@code upozorenja} su tekstovi
 * za Novicu, redom iz dogovora. Obrisana grupa: {@code grupaId} i {@code grupaNaziv} su {@code null}, {@code putanja} i
 * {@code iskljucenePodgrupe} prazne, jedino upozorenje je da je grupa obrisana. {@code primerSvgId} (poslednje, dodato
 * kasnije) je SVG teksta jedne varijante koju bi izvlačenje dalo ({@code KandidatiZadatka.StabloPredavanja#primer}), da
 * Novica u editoru vidi primer zadatka; {@code null} bez kandidata nivoa slajda. Ide samo nastavniku (editor, konzola);
 * MCP ga ne nosi ({@code McpZadatakSazetak}), telefon i tabla ga nikad ne dobijaju.
 */
public record ZadatakSlajdaInfo(Long grupaId, Integer vremeSekunde, Integer brojVarijanti, Integer nivo, String grupaNaziv,
                                List<GrupaPutanja> putanja, boolean list, int brojAktivnihVarijanti,
                                List<Integer> brojAktivnihPoNivou, List<Polje> polja, boolean iskljucena,
                                List<GrupaPutanja> iskljucenePodgrupe, List<String> upozorenja, String primerSvgId) {

    /** Kandidati nivoa {@code nivo} (1-3); {@code null} = zbir svih nivoa. */
    public int brojAktivnih(Integer nivo) {
        List<Integer> po = brojAktivnihPoNivou == null ? List.of() : brojAktivnihPoNivou;
        if (nivo == null) return po.stream().mapToInt(Integer::intValue).sum();
        return nivo >= 1 && nivo <= po.size() ? po.get(nivo - 1) : 0;
    }
}
