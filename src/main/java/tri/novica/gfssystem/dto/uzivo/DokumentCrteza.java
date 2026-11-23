package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Telo upisa strane crteža ili table predavanja: ceo niz elemenata (zamenjuje prethodni). */
public record DokumentCrteza(List<ElementCrteza> elementi) {
}
