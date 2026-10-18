package tri.novica.gfssystem.dto.banka;

import tools.jackson.databind.annotation.JsonDeserialize;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.service.banka.PoljaJson;
import tri.novica.gfssystem.utility.CeoBrojDeserializer;

import java.util.List;

/**
 * Grupa zadataka za {@code POST /banka/grupe} (nova) i {@code PUT /banka/grupe/{id}} (puna zamena naziva, opisa,
 * težine i uputstva: izostavljeno je prazno, kao {@code SlajdCmd}). {@code polja}: kod nove grupe {@code null} = bez
 * polja ({@code []}); kod izmene {@code null} = šema i odgovori varijanti ostaju nepromenjeni, a brisanje šeme traži
 * eksplicitno {@code []}. {@code predmetId} i {@code namena} su obavezni samo za koren; podgrupa ih nasleđuje od
 * roditelja ({@code roditeljId}) i ovde se zanemaruju. Izmena ne premešta grupu: {@code predmetId}, {@code roditeljId}
 * i {@code namena} tada ne važe ({@code POST .../premesti}). {@code polja} se čitaju strogo ({@link PoljaJson#polja}:
 * nepoznato svojstvo je 400), {@code tezina} samo kao ceo broj.
 */
public record GrupaCmd(Long predmetId, Long roditeljId, Namena namena, String naziv, String opis,
                       @JsonDeserialize(using = CeoBrojDeserializer.class) Integer tezina, String uputstvo,
                       @JsonDeserialize(using = PoljaJson.StrogaPolja.class) List<Polje> polja) {
}
