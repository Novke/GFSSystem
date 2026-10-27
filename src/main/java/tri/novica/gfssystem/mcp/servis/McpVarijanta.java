package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Rezultat alata {@code varijanta} (spec MCP 4.3): {@code VarijantaDetails} bez id-jeva SVG-a ({@code svgId},
 * {@code resenjeSvgId} su javni URL-ovi, modelu ne trebaju), sa namenom grupe (od nje zavisi da li slika nosi rešenje),
 * prilozima kao ime i id medija i linkom varijante u UI-ju. {@code renderGreska} i {@code renderDeo} kao u
 * {@code VarijantaDetails}: posle neuspelog rendera linija i poruka, i deo ({@code tekst} ili {@code resenje}).
 * {@code nivo} 1-3: koliko je teška varijanta unutar svog tipa (težina tipa je {@code tezina} grupe).
 */
public record McpVarijanta(Long id, Long grupaId, List<GrupaPutanja> putanja, Namena namena, String naziv, String tekst,
                           String resenje, Map<String, OdgovorPolja> odgovori, List<Polje> polja,
                           List<McpPrilog> prilozi, StatusVarijante status, IzvorVarijante izvor, boolean ikadOdobrena,
                           Long zamenjuje, String serija, int nivo, RenderStatus renderStatus, RenderGreskaInfo renderGreska,
                           String renderDeo, LocalDateTime izmenjeno, String link) {
}
