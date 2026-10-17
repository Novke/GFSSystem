package tri.novica.gfssystem.dto.banka;

import java.util.List;

/**
 * Čvor stabla grupa ({@code GET /banka/stablo}); {@code deca} po {@code rb}, {@code list} = nema podgrupa. Brojači:
 * {@code brojVarijanti} sve varijante (i arhivirane), {@code brojAktivnih} AKTIVNA, {@code brojNacrta} NACRT,
 * {@code brojGresaka} render GRESKA (u bilo kom statusu); za unutrašnji čvor zbir celog podstabla.
 */
public record GrupaCvor(Long id, String naziv, int rb, Integer tezina, boolean list, int brojVarijanti,
                        int brojAktivnih, int brojNacrta, int brojGresaka, List<GrupaCvor> deca) {
}
