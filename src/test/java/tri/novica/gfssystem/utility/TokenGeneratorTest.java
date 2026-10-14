package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenGeneratorTest {

    @Test
    void tokenIma32ZnakaIzAlfabeta() {
        TokenGenerator generator = new TokenGenerator();
        for (int i = 0; i < 100; i++) {
            String token = generator.novi();
            assertEquals(32, token.length(), token);
            for (char c : token.toCharArray()) {
                assertTrue(TokenGenerator.ALFABET.indexOf(c) >= 0, "znak van alfabeta: " + c);
            }
            assertTrue(TokenGenerator.FORMAT.matcher(token).matches(), token);
        }
    }

    @Test
    void alfabetNemaSlicneZnake() {
        for (char c : "0Oo1lIi".toCharArray()) {
            assertEquals(-1, TokenGenerator.ALFABET.indexOf(c), "sličan znak u alfabetu: " + c);
        }
    }
}
