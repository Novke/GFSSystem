package tri.novica.gfssystem.dto.cas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token iz QR-a ({@code <predavanjeId>.<22 znaka>}); oblik proverava servis (pogrešan = 410 kao istekao). */
public record SkeniranjeCmd(@NotBlank @Size(max = 64) String token) {
}
