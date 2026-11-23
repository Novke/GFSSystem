package tri.novica.gfssystem.dto.anketa;

import tri.novica.gfssystem.entity.anketa.PublikaAnkete;

import java.time.LocalDateTime;

/** Red istorije anketa ({@code GET /ankete}): ishod je {@code pobednik} (tekst odgovora ili {@code null} kod nerešenog). */
public record AnketaInfo(Long id, String pitanje, PublikaAnkete publika, LocalDateTime otvoreno, LocalDateTime zatvoreno,
                         boolean otvorena, int brojGlasalo, int brojMogucih, String pobednik, String predmetNaziv,
                         String grupaNaziv) {
}
