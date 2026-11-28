package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Stavka šablona u komandi: {@code nivoi} prazno = svi, {@code izvori} 1-10 čvorova banke TEST istog predmeta. */
public record StavkaSablonaCmd(String naziv, Integer poeni, Integer broj, List<Integer> nivoi, Boolean istiTip, Boolean rucno, List<Long> izvori) {
}
