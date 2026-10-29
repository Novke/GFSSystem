package tri.novica.gfssystem.dto.portal;

import tri.novica.gfssystem.dto.banka.Polje;

import java.util.List;

/**
 * Izvučen zadatak za vežbanje (spec 5.4): pokušaj, tip i tema, nivo varijante, SVG teksta i šema polja <b>bez</b> tačnih
 * odgovora ({@code polja} prazno = rad na papiru). Rešenje i tačne vrednosti tek kroz "Prikaži rešenje".
 */
public record ZadatakVezbanja(Long pokusajId, Long tipId, String tip, String tema, int nivo, String svgId, List<Polje> polja) {
}
