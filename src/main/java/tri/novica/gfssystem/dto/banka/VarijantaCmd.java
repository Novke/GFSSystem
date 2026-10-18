package tri.novica.gfssystem.dto.banka;

import tools.jackson.databind.annotation.JsonDeserialize;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.service.banka.PoljaJson;

import java.util.List;
import java.util.Map;

/**
 * Varijanta za {@code POST /banka/grupe/{id}/varijante} i {@code PUT /banka/varijante/{id}} (puna zamena naziva, teksta,
 * rešenja, odgovora i priloga: izostavljeno je prazno). {@code odgovori} se čitaju strogo
 * ({@link PoljaJson#odgovori}: nepoznato svojstvo, {@code null} vrednost ili pogrešan tip je 400) i moraju pokriti
 * svako polje šeme grupe. {@code izvor} važi samo pri kreiranju ({@code null} = {@code RUCNO}); izmena ga ne menja.
 */
public record VarijantaCmd(String naziv, String tekst, String resenje,
                           @JsonDeserialize(using = PoljaJson.StrogiOdgovori.class) Map<String, OdgovorPolja> odgovori,
                           List<PrilogCmd> prilozi, IzvorVarijante izvor) {
}
