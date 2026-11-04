package tri.novica.gfssystem.dto.banka;

import java.util.List;

/** Odgovor {@code POST /banka/varijante/povuci-odobrenje}. */
public record PovlacenjeInfo(List<Long> vracene, List<PreskocenaVarijanta> preskocene) {
}
