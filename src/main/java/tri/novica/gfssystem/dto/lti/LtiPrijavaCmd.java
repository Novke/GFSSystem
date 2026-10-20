package tri.novica.gfssystem.dto.lti;

/**
 * Parametri OIDC login initiation-a koje Moodle šalje ({@code GET} ili {@code POST /public/lti/prijava}); svi mogu
 * nedostajati (servis tada preusmerava na stranicu greške, ne 400).
 */
public record LtiPrijavaCmd(String iss, String loginHint, String targetLinkUri, String ltiMessageHint,
                            String clientId, String ltiDeploymentId) {}
