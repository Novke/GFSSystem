package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.banka.GrupaPutanja;

import java.util.List;
import java.util.Map;

/**
 * Runda zadatka u pregledu izvođenja (sve vidljivo). {@code slajdId} je iz snimka, pa ostaje i kad slajda više nema
 * ({@code RezultatPitanja.slajdId} je tada {@code null}). {@code nivo}: nivo runde ({@code null} = svi nivoi ili runda od
 * pre nivoa varijante); {@code nivoi}: varijantaId -> nivo izvučenih varijanti (prazna za rundu od pre nivoa).
 */
public record PregledZadatka(Long slajdId, String grupaNaziv, List<GrupaPutanja> putanja, ZadatakRezultat rezultat,
                             List<UcesnikZadatka> ucesnici, Integer nivo, Map<Long, Integer> nivoi) {
}
