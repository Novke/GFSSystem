package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.banka.GrupaPutanja;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Snimak runde Zadatak slajda: čvor banke ({@code grupaId}, naziv i putanja od korena) i samo izvučene varijante, sa
 * šemom i tačnim odgovorima kakvi su bili pri otvaranju (spec 11, banka spec 4).
 */
public record ZadatakSnimak(Long slajdId, Long grupaId, String grupaNaziv, List<GrupaPutanja> putanja,
                            Integer vremeSekunde, List<VarijantaSnimak> varijante) implements SnimakRunde {

    /** Tačno/netačno postoji kad bar jedna izvučena varijanta ima polja (bez polja je rad na papiru). */
    @Override
    public boolean imaTacanOdgovor() {
        return varijante != null && varijante.stream().anyMatch(VarijantaSnimak::imaPolja);
    }

    /** Da li bar jedna izvučena varijanta ima rešenje (uslov za uključenje RESENJE). */
    public boolean imaResenje() {
        return varijante != null && varijante.stream().anyMatch(v -> v.resenjeSvgId() != null);
    }

    /** Izvučena varijanta sa tim id-jem; prazno za nepoznat ili {@code null} id. */
    public Optional<VarijantaSnimak> varijanta(Long varijantaId) {
        if (varijantaId == null || varijante == null) return Optional.empty();
        return varijante.stream().filter(v -> Objects.equals(v.varijantaId(), varijantaId)).findFirst();
    }
}
