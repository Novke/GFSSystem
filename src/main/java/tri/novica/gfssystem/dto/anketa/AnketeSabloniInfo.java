package tri.novica.gfssystem.dto.anketa;

import java.util.List;

/**
 * Sva sačuvana pitanja i folderi ({@code GET /ankete/sabloni}): folderi po {@code rb}; šabloni prvo prečice po
 * {@code precicaRb}, pa ostali po folderu ({@code rb}, {@code id}).
 */
public record AnketeSabloniInfo(List<FolderAnkete> folderi, List<SablonAnkete> sabloni) {
}
