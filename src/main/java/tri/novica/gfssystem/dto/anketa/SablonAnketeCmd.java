package tri.novica.gfssystem.dto.anketa;

import tri.novica.gfssystem.entity.anketa.BojaPrecice;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;

import java.util.List;

/**
 * Novo sačuvano pitanje ili cela zamena postojećeg ({@code POST|PUT /ankete/sabloni}): {@code null} za bool polja je
 * {@code false}, za {@code publika} {@code NA_CASU}; prazni tekstovi izgleda prečice su {@code null}. {@code trajanjeSekunde}
 * važi samo za {@code NA_CASU}, {@code rokSati} samo za {@code GRUPA} (drugo se briše). Pravila su u {@code AnketaPravila}.
 */
public record SablonAnketeCmd(Long folderId, String pitanje, List<String> opcije, Boolean viseOdgovora, PublikaAnkete publika,
                              Integer trajanjeSekunde, Integer rokSati, Boolean precica, String ikonica, BojaPrecice boja,
                              String kratakNaziv, Boolean pokreniOdmah) {
}
