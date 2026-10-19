package tri.novica.gfssystem.service.uzivo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Snimak runde ({@code PitanjeRunda.snimak}, JSON): kopija slajda u trenutku otvaranja, po kojoj se ocenjuje i prikazuje i
 * kad se slajd (ili banka) kasnije izmeni ili obriše. {@code vrsta} bira podtip; snimak bez {@code vrsta} (upisan pre
 * Zadatak slajda) čita se kao {@link PitanjeSnimak}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "vrsta",
        defaultImpl = PitanjeSnimak.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PitanjeSnimak.class, name = "PITANJE"),
        @JsonSubTypes.Type(value = ZadatakSnimak.class, name = "ZADATAK")})
public sealed interface SnimakRunde permits PitanjeSnimak, ZadatakSnimak {

    /** Slajd runde u trenutku otvaranja (ostaje i kad slajda više nema). */
    Long slajdId();

    /** Da li odgovor može biti tačan ili netačan. */
    boolean imaTacanOdgovor();
}
