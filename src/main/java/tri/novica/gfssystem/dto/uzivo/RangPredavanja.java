package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Rang tačnosti predavanja: izvođenja predavanja koja imaju bar jednu rundu (najnovije prvo) i zbir svih njih
 * ({@code GET /predavanja/{id}/rang}).
 */
public record RangPredavanja(Long predavanjeId, List<IzvodjenjeInfo> izvodjenja, List<RangTacnostiStavka> rang) {
}
