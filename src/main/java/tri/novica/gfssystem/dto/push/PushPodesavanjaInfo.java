package tri.novica.gfssystem.dto.push;

import java.util.List;

/** Nastavnička podešavanja push-a ({@code GET|PUT /push/podesavanja}): sve vrste, redom kao u {@code VrstaObavestenja}. */
public record PushPodesavanjaInfo(List<PushVrstaInfo> vrste) {
}
