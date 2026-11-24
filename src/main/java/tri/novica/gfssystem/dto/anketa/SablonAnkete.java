package tri.novica.gfssystem.dto.anketa;

import tri.novica.gfssystem.entity.anketa.BojaPrecice;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;

import java.time.LocalDateTime;
import java.util.List;

/** Sačuvano pitanje ankete sa izgledom prečice ({@code precicaRb} je {@code null} kad nije prečica). */
public record SablonAnkete(Long id, Long folderId, String pitanje, List<String> opcije, boolean viseOdgovora,
                           PublikaAnkete publika, Integer trajanjeSekunde, Integer rokSati, boolean precica, Integer precicaRb,
                           String ikonica, BojaPrecice boja, String kratakNaziv, boolean pokreniOdmah, int rb,
                           int brojPokretanja, LocalDateTime poslednjePokretanje) {
}
