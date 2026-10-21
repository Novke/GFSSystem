package tri.novica.gfssystem.dto.banka;

import java.util.List;

/** Novi redosled dece jednog roditelja (ili korena): tačno svi njihovi id-jevi, svaki jednom. */
public record RedosledGrupaCmd(List<Long> ids) {
}
