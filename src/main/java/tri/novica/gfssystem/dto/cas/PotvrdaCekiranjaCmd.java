package tri.novica.gfssystem.dto.cas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ključ čekanja iz {@code sessionStorage} telefona; oblik proverava servis (pogrešan = 410 kao istekao). */
public record PotvrdaCekiranjaCmd(@NotBlank @Size(max = 64) String kljuc) {
}
