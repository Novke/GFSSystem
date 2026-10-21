package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.banka.IzvorVarijante;

import java.util.List;

/**
 * Čvor stabla grupa ({@code GET /banka/stablo}); {@code deca} po {@code rb}, {@code list} = nema podgrupa. Brojači:
 * {@code brojVarijanti} sve varijante (i arhivirane), {@code brojAktivnih} AKTIVNA, {@code brojNacrta} NACRT,
 * {@code brojGresaka} render GRESKA (u bilo kom statusu), {@code brojClaudeNacrta} NACRT sa izvorom CLAUDE (čekaju
 * Novicin pregled); za unutrašnji čvor zbir celog podstabla. {@code izvor} je ko je napravio grupu. {@code iskljucena}:
 * grupa ili neki njen predak ima isključenu temu, a {@code iskljucenjeId} je id teme najbližeg takvog čvora (sopstvena
 * pre pretka), inače {@code null}.
 */
public record GrupaCvor(Long id, String naziv, int rb, Integer tezina, boolean list, int brojVarijanti,
                        int brojAktivnih, int brojNacrta, int brojGresaka, List<GrupaCvor> deca, IzvorVarijante izvor,
                        int brojClaudeNacrta, boolean iskljucena, Long iskljucenjeId) {
}
