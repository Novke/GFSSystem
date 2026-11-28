package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;

/** Smena testa (u fazi 1 jedna). */
public record SmenaTesta(Long id, String ulazniKod, LocalDateTime ulazOtvoren, LocalDateTime pokrenuta, Long rokMs, LocalDateTime zatvorena) {
}
