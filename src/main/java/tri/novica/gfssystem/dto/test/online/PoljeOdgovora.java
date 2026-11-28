package tri.novica.gfssystem.dto.test.online;

import java.util.List;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;

/** Polje varijante u pregledu po odgovorima. */
public record PoljeOdgovora(String kljuc, String oznaka, TipPolja tip, double poeni, boolean konacno, List<PoljeOpcija> opcije, OdgovorPolja tacno, boolean ponisteno, int prazno, List<GrupaOdgovora> grupe) {
}
