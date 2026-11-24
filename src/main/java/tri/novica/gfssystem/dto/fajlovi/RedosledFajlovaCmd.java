package tri.novica.gfssystem.dto.fajlovi;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Novi redosled: tačno permutacija id-jeva svih fajlova prezentacije. */
public record RedosledFajlovaCmd(@NotNull(message = "Redosled je obavezan.") List<Long> ids) {
}
