package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Pregled po odgovorima sa važećim ispravkama. */
public record OdgovoriTesta(List<VarijantaOdgovora> varijante, List<IspravkaTesta> ispravke) {
}
