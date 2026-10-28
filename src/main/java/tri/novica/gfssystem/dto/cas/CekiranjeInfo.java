package tri.novica.gfssystem.dto.cas;

import java.util.List;

/**
 * Čekiranje predavanja za nastavnika ({@code GET /predavanja/{id}/cekiranje}): token QR-a tekućeg prozora i kraj prozora
 * (epoch ms), serversko vreme, upozorenje kad datum predavanja nije današnji i spisak čekiranih po vremenu. {@code token} i
 * {@code vaziDoMs} su {@code null} kad predavanje nije živo. Tajna nikad ne izlazi.
 */
public record CekiranjeInfo(String token, Long vaziDoMs, long serverVremeMs, boolean datumNijeDanas,
                            List<CekiranStudent> cekirani) {
}
