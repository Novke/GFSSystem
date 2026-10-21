package tri.novica.gfssystem.dto.lti;

import java.time.LocalDateTime;

/** Jedna LTI registracija (ugovor sa frontom: api-contract.md, "Moodle (LTI)"). */
public record LtiRegistracijaInfo(Long id, String naziv, String izdavac, String clientId, String deploymentId,
                                  String authUrl, String jwksUrl, boolean aktivna, LocalDateTime kreirano) {
}
