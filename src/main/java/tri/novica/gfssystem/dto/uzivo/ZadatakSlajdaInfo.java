package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.Polje;

import java.util.List;

/**
 * Zadatak slajda sa opisom čvora banke (izlaz; editor, konzola i MCP). Prva tri polja su ono što je na slajdu, ostalo se
 * računa iz banke u trenutku čitanja ({@code ZadaciSlajdova}): {@code putanja} od korena do grupe, {@code list} (grupa nema
 * podgrupa), {@code brojAktivnihVarijanti} = kandidati (AKTIVNE sa SVG-om u listovima koji se izvlače, bez isključenih
 * podgrupa), {@code polja} = šema za list ({@code null} za unutrašnji čvor, prazna lista = rad na papiru), {@code iskljucena}
 * = čvor ili neki njegov predak ima isključenu temu, {@code iskljucenePodgrupe} se ne izvlače. {@code upozorenja} su tekstovi
 * za Novicu, redom iz dogovora. Obrisana grupa: {@code grupaId} i {@code grupaNaziv} su {@code null}, {@code putanja} i
 * {@code iskljucenePodgrupe} prazne, jedino upozorenje je da je grupa obrisana.
 */
public record ZadatakSlajdaInfo(Long grupaId, Integer vremeSekunde, Integer brojVarijanti, String grupaNaziv,
                                List<GrupaPutanja> putanja, boolean list, int brojAktivnihVarijanti, List<Polje> polja,
                                boolean iskljucena, List<GrupaPutanja> iskljucenePodgrupe, List<String> upozorenja) {
}
