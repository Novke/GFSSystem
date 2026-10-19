package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.utility.Promena;

/**
 * Klodova delimična izmena grupe (merge patch, spec MCP 4.1): {@link Promena#bez()} ostavlja polje kakvo jeste,
 * {@code Promena.na(null)} ga briše. Brišu se samo {@code opis}, {@code tezina} i {@code uputstvo} (pravilo nosi
 * {@code McpPatch} u alatu); servis spaja patch nad zaključanim stanjem i proverava rezultat kao {@link GrupaCmd}. Šema
 * polja nije deo patch-a (posebna izmena sa probom, {@code GrupaZadatakaService.izmeniPolja}). Polje {@code null} (ne
 * {@code Promena}) znači "nije poslato".
 */
public record GrupaPatch(Promena<String> naziv, Promena<String> opis, Promena<Integer> tezina, Promena<String> uputstvo) {

    public GrupaPatch {
        naziv = naziv == null ? Promena.bez() : naziv;
        opis = opis == null ? Promena.bez() : opis;
        tezina = tezina == null ? Promena.bez() : tezina;
        uputstvo = uputstvo == null ? Promena.bez() : uputstvo;
    }
}
