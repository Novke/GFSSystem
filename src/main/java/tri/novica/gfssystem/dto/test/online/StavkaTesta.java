package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Prepis stavke na testu. */
public record StavkaTesta(Long id, int rb, String naziv, int poeni, int broj, List<Integer> nivoi, Integer izabraniNivo, boolean istiTip, boolean rucno, List<IzvorStavke> izvori, List<TipStavke> tipovi) {
}
