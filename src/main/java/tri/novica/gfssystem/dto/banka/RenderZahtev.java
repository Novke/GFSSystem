package tri.novica.gfssystem.dto.banka;

import java.util.List;

/** Telo {@code POST /render} render servisa; {@code format} je {@code svg} ili {@code pdf}, {@code timeoutSekunde} je granica kompilacije u servisu. */
public record RenderZahtev(String format, String tex, List<RenderPrilog> prilozi, int timeoutSekunde) {
}
