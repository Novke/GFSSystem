package tri.novica.gfssystem.dto.anketa;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Redosled id-jeva: prečice ({@code PUT /ankete/precice}, ostali šabloni prestaju da budu prečice) ili folderi
 * ({@code PUT /ankete/folderi/redosled}, tačno svi folderi, svaki jednom).
 */
public record RedosledAnketaCmd(@NotNull(message = "Redosled je obavezan.") List<Long> ids) {
}
