package tri.novica.gfssystem.dto.banka;

import java.util.List;

/** Ishod masovne radnje nad varijantama: svaka varijanta je svoja transakcija, pa jedna greška ne obara ostale. */
public record IshodVarijanti(List<Long> uspele, List<PreskocenaVarijanta> preskocene) {
}
