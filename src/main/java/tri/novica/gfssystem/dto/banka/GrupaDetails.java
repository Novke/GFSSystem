package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.banka.Namena;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Grupa zadataka sa šemom polja i putanjom (od korena do nje same). {@code list} = nema podgrupa (i kad nema
 * varijanti). Brojači su kao u {@link GrupaCvor}: za unutrašnji čvor zbir podstabla.
 */
public record GrupaDetails(Long id, Long predmetId, Long roditeljId, Namena namena, String naziv, String opis,
                           int rb, Integer tezina, String uputstvo, List<Polje> polja, List<GrupaPutanja> putanja,
                           boolean list, int brojVarijanti, int brojAktivnih, int brojNacrta, int brojGresaka,
                           LocalDateTime izmenjeno) {
}
