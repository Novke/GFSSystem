package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.NacinBodovanja;
import tri.novica.gfssystem.entity.banka.Namena;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Grupa zadataka sa šemom polja i putanjom (od korena do nje same). {@code list} = nema podgrupa (i kad nema
 * varijanti). Brojači su kao u {@link GrupaCvor}: za unutrašnji čvor zbir podstabla. {@code izvor} i {@code serija}:
 * ko je napravio grupu i Klodova serija upisa ({@code null} za ručno napravljene); {@code iskljucena} i
 * {@code iskljucenjeId} kao u {@link GrupaCvor}. {@code bodovanje} i {@code konacnoPriznajeKorake}: bodovanje na online
 * testu (spec online testova 6.2).
 */
public record GrupaDetails(Long id, Long predmetId, Long roditeljId, Namena namena, String naziv, String opis,
                           int rb, Integer tezina, String uputstvo, List<Polje> polja, List<GrupaPutanja> putanja,
                           boolean list, int brojVarijanti, int brojAktivnih, int brojNacrta, int brojGresaka,
                           LocalDateTime izmenjeno, IzvorVarijante izvor, String serija, boolean iskljucena,
                           Long iskljucenjeId, NacinBodovanja bodovanje, boolean konacnoPriznajeKorake) {

    /** Oblik pre online testova (bodovanje po poljima, bez priznavanja koraka). */
    public GrupaDetails(Long id, Long predmetId, Long roditeljId, Namena namena, String naziv, String opis,
                        int rb, Integer tezina, String uputstvo, List<Polje> polja, List<GrupaPutanja> putanja,
                        boolean list, int brojVarijanti, int brojAktivnih, int brojNacrta, int brojGresaka,
                        LocalDateTime izmenjeno, IzvorVarijante izvor, String serija, boolean iskljucena,
                        Long iskljucenjeId) {
        this(id, predmetId, roditeljId, namena, naziv, opis, rb, tezina, uputstvo, polja, putanja, list, brojVarijanti,
                brojAktivnih, brojNacrta, brojGresaka, izmenjeno, izvor, serija, iskljucena, iskljucenjeId,
                NacinBodovanja.PO_POLJIMA, false);
    }
}
