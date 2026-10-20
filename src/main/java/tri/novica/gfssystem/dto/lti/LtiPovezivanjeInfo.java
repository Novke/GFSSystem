package tri.novica.gfssystem.dto.lti;

/** {@code GET /public/lti/povezivanje}: ime Moodle naloga iz ulaska ({@code null} kad ga Moodle nije podelio). */
public record LtiPovezivanjeInfo(String ime) {}
