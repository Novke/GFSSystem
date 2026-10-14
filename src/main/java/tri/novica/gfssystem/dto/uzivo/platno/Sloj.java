package tri.novica.gfssystem.dto.uzivo.platno;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Sloj platna; {@code tip} u JSON-u bira podklasu. Zajednička polja su ista u svim slojevima. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "tip")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TekstSloj.class, name = "TEKST"),
        @JsonSubTypes.Type(value = SlikaSloj.class, name = "SLIKA"),
        @JsonSubTypes.Type(value = OblikSloj.class, name = "OBLIK"),
        @JsonSubTypes.Type(value = TabelaSloj.class, name = "TABELA"),
        @JsonSubTypes.Type(value = FormulaSloj.class, name = "FORMULA"),
        @JsonSubTypes.Type(value = CrtezSloj.class, name = "CRTEZ")})
public sealed interface Sloj permits TekstSloj, SlikaSloj, OblikSloj, TabelaSloj, FormulaSloj, CrtezSloj {

    String id();

    String naziv();

    Double x();

    Double y();

    Double sirina();

    Double visina();

    Double rotacija();

    Double providnost();

    Boolean zakljucan();

    Senka senka();
}
