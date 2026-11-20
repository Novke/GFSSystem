package tri.novica.gfssystem.dto.fajlovi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Izmena fajla: novo ime (ekstenzija originala se zadržava, {@code MedijService.normalizujNaziv}) i slajd ({@code null} = uz celu prezentaciju). */
public record FajlPrezentacijeCmd(
        @NotBlank(message = "Naziv fajla je obavezan.")
        @Size(max = 200, message = "Naziv fajla može imati najviše 200 znakova.")
        String naziv,
        Long slajdId) {
}
