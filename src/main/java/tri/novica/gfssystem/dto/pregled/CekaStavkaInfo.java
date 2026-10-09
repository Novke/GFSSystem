package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;

import java.time.LocalDateTime;

/** Onboarding sesija sa prijavama na čekanju (kontrolna tabla, "čeka na tebe"). */
public record CekaStavkaInfo(Long sesijaId, GrupaInfo grupa, Long brojNaCekanju, LocalDateTime istice) {
}
