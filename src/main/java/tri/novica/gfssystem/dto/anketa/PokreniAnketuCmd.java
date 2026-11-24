package tri.novica.gfssystem.dto.anketa;

import tri.novica.gfssystem.entity.anketa.PublikaAnkete;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pokretanje ankete ({@code POST /ankete}): pitanje i odgovori se uvek šalju (i kad je iz šablona: ono što je nastavnik
 * izmenio pri pokretanju ostaje samo u anketi). {@code NA_CASU} traži {@code predavanjeId} (grupa je grupa predavanja) i
 * opciono {@code trajanjeSekunde}; {@code GRUPA} traži {@code rok} i {@code grupaId} ili {@code predavanjeId}. {@code publika}
 * {@code null} = {@code NA_CASU}.
 */
public record PokreniAnketuCmd(Long sablonId, String pitanje, List<String> opcije, Boolean viseOdgovora, PublikaAnkete publika,
                               Long predavanjeId, Long grupaId, Integer trajanjeSekunde, LocalDateTime rok) {
}
