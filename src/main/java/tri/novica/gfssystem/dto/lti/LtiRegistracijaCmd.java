package tri.novica.gfssystem.dto.lti;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registracija alata na LTI 1.3 platformi (Moodle), {@code POST|PUT /lti/registracije}. Sve adrese su {@code https://}.
 * Prazni {@code authUrl} i {@code jwksUrl} se izvode iz {@code izdavac}: {@code /mod/lti/auth.php} i
 * {@code /mod/lti/certs.php}. {@code aktivna} bez vrednosti znači aktivna. Ništa od ovoga nije tajna.
 */
public record LtiRegistracijaCmd(
        @NotBlank(message = "Naziv je obavezan.")
        @Size(max = 120, message = "Naziv je predugačak.")
        String naziv,
        @NotBlank(message = "Izdavač (issuer) je obavezan.")
        @Size(max = 255, message = "Izdavač je predugačak.")
        String izdavac,
        @NotBlank(message = "Client ID je obavezan.")
        @Size(max = 255, message = "Client ID je predugačak.")
        String clientId,
        @NotBlank(message = "Deployment ID je obavezan.")
        @Size(max = 255, message = "Deployment ID je predugačak.")
        String deploymentId,
        @Size(max = 500, message = "Auth URL je predugačak.")
        String authUrl,
        @Size(max = 500, message = "JWKS URL je predugačak.")
        String jwksUrl,
        Boolean aktivna) {
}
