package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Jedna strana crteža (ili tabla predavanja); nepostojeća je {@code {elementi: [], verzija: 0}}. */
public record StranaCrteza(List<ElementCrteza> elementi, int verzija) {
}
