package tri.novica.gfssystem.utility;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TelefonTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0641234567|+381641234567",
            "064 123 4567|+381641234567",
            "064/123-45-67|+381641234567",
            "(064) 123 45 67|+381641234567",
            "+381641234567|+381641234567",
            "+381 64 123 4567|+381641234567",
            "00381641234567|+381641234567",
            "381641234567|+381641234567",
            "+387651234567|+387651234567",
            "0038765123456|+38765123456",
            "641234567|641234567",
    })
    void broj(String unos, String ocekivano) {
        assertEquals(ocekivano, Telefon.uMedjunarodni(unos));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "-", "nema"})
    void bezCifaraJeNull(String unos) {
        assertNull(Telefon.uMedjunarodni(unos));
    }
}
