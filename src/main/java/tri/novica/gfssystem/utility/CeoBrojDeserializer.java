package tri.novica.gfssystem.utility;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * {@code Integer} koji prihvata samo JSON ceo broj. Podrazumevani deserijalizator tiho odseca decimalni broj
 * ({@code 20.5} postaje 20, {@code 20.0} takođe) i čita string "20" kao broj; ovde su to greške (400 preko
 * {@code ApiExceptionHandler}). Za {@code null} se ne poziva, pa ostaje {@code null}. Uključuje se samo na polju ili
 * parametru: {@code @JsonDeserialize(using = CeoBrojDeserializer.class)}.
 */
public class CeoBrojDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return (Integer) ctxt.handleUnexpectedToken(Integer.class, p);
        }
        return p.getIntValue();   // izvan opsega int je InputCoercionException, takođe 400
    }
}
