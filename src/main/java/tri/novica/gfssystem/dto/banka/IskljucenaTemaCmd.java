package tri.novica.gfssystem.dto.banka;

/**
 * Nova isključena tema predmeta ({@code POST /predmeti/{id}/iskljucene-teme}, MCP {@code iskljuci_temu}): {@code opis}
 * 1-300 znakova (posle trim-a), {@code grupaId} opciono. Sa grupom isključuje tu grupu i celo njeno podstablo; bez grupe
 * je samo opis teme koja u banci još ne postoji, da je Klod ne napravi.
 */
public record IskljucenaTemaCmd(String opis, Long grupaId) {
}
