package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.banka.GrupaPutanja;

import java.util.List;

/**
 * Runda zadatka u pregledu izvođenja (sve vidljivo). {@code slajdId} je iz snimka, pa ostaje i kad slajda više nema
 * ({@code RezultatPitanja.slajdId} je tada {@code null}).
 */
public record PregledZadatka(Long slajdId, String grupaNaziv, List<GrupaPutanja> putanja, ZadatakRezultat rezultat,
                             List<UcesnikZadatka> ucesnici) {
}
