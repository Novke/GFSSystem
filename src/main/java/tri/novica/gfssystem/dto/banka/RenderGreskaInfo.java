package tri.novica.gfssystem.dto.banka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Greška kompilacije: {@code linija} relativna na tekst nastavnika (od 1) ili {@code null} kad se ne zna. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenderGreskaInfo(Integer linija, String poruka) {
}
