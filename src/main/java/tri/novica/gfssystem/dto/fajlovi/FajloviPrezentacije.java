package tri.novica.gfssystem.dto.fajlovi;

import java.time.LocalDateTime;
import java.util.List;

/** Fajlovi jedne prezentacije koje nalog studenta otvara ({@code odrzana} = prvi početak završenog izvođenja njegove grupe). */
public record FajloviPrezentacije(Long prezentacijaId, String naziv, LocalDateTime odrzana, List<FajlKratko> fajlovi) {
}
