package tri.novica.gfssystem.dto.test.online;

import java.util.List;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;

/** Polje u rezultatu za studenta; {@code tacno} samo uz objavu sa tačnim odgovorima. */
public record PoljeRezultata(String kljuc, String oznaka, TipPolja tip, List<PoljeOpcija> opcije, String jedinica, Object uneto, double udeo, OdgovorPolja tacno) {
}
