package tri.novica.gfssystem.dto.lti;

/**
 * Vrednosti koje nastavnik upisuje u Moodle formu alata (spec 8.1). {@code toolUrl} i {@code redirectionUri} su ista
 * adresa ({@code .../api/public/lti/start}); host je prvi unos {@code gfs.front.url}.
 */
public record LtiVrednostiZaMoodleInfo(String toolUrl, String initiateLoginUrl, String redirectionUri,
                                       String publicKeysetUrl) {
}
