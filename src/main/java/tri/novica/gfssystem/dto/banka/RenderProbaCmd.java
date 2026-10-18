package tri.novica.gfssystem.dto.banka;

import java.util.List;

/** {@code POST /banka/render-proba}: render bez čuvanja, sa preambulom predmeta {@code predmetId}; rešenje i prilozi su opcioni. */
public record RenderProbaCmd(Long predmetId, String tekst, String resenje, List<PrilogCmd> prilozi) {
}
