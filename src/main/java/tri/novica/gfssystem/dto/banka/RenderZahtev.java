package tri.novica.gfssystem.dto.banka;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Telo {@code POST /render} render servisa; {@code timeoutSekunde} je granica kompilacije u servisu. {@code format}
 * ({@code svg} ili {@code pdf}) je jedini format starog oblika zahteva; {@code formati} (npr. {@code ["svg","png"]}, svi iz
 * istog prevođenja) ima prednost i izostaje iz JSON-a kad je {@code null}, pa stari poziv šalje isto telo kao pre PNG-a.
 */
public record RenderZahtev(String format, String tex, List<RenderPrilog> prilozi, int timeoutSekunde,
                           @JsonInclude(JsonInclude.Include.NON_NULL) List<String> formati) {

    /** Stari oblik zahteva: jedan format, bez {@code formati}. */
    public RenderZahtev(String format, String tex, List<RenderPrilog> prilozi, int timeoutSekunde) {
        this(format, tex, prilozi, timeoutSekunde, null);
    }
}
