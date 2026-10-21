package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Cela varijanta sa putanjom grupe i šemom polja grupe ({@code polja}). Posle neuspelog rendera {@code renderGreska}
 * nosi liniju (relativnu na deo) i poruku, a {@code renderDeo} deo u kome je greška: {@code "tekst"} ili
 * {@code "resenje"}; inače su oba {@code null}. {@code prilozi} su po imenu. {@code ikadOdobrena}, {@code zamenjuje} i
 * {@code serija} kao u {@link VarijantaInfo}.
 */
public record VarijantaDetails(Long id, Long grupaId, List<GrupaPutanja> putanja, int rb, String naziv, String tekst,
                               String resenje, Map<String, OdgovorPolja> odgovori, List<Polje> polja,
                               List<PrilogInfo> prilozi, StatusVarijante status, IzvorVarijante izvor,
                               RenderStatus renderStatus, String svgId, String resenjeSvgId, RenderGreskaInfo renderGreska,
                               String renderDeo, LocalDateTime kreirano, LocalDateTime izmenjeno, boolean ikadOdobrena,
                               Long zamenjuje, String serija) {
}
