package tri.novica.gfssystem.dto.portal;

import java.util.List;

/** Tema (koren) sa svojim tipovima otvorenim za vežbanje, redom stabla. */
public record TemaVezbanja(String naziv, List<TipVezbanja> tipovi) {
}
