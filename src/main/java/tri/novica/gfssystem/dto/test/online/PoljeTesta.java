package tri.novica.gfssystem.dto.test.online;

import java.util.List;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;

/** Polje zadatka za studenta: šema bez tačnih vrednosti, plus deo poena zadatka koji polje nosi. */
public record PoljeTesta(String kljuc, String oznaka, TipPolja tip, Double odstupanje, OdstupanjeTip odstupanjeTip, String jedinica, Boolean zanemariVelicinu, Boolean zanemariRazmake, List<PoljeOpcija> opcije, Boolean zanemariVodeceNule, Double poeni, Boolean konacno, double poeniPolja) {
}
