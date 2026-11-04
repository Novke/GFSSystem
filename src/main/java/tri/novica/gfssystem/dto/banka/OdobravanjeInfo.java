package tri.novica.gfssystem.dto.banka;

import java.util.List;

/** Odgovor {@code POST /banka/varijante/odobri-vise}. */
public record OdobravanjeInfo(List<Long> odobrene, List<PreskocenaVarijanta> preskocene) {
}
