package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Novi redosled: tačno permutacija id-jeva svih slajdova prezentacije. */
public record RedosledCmd(List<Long> slajdIds) {
}
