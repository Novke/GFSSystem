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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0641234567|+381641234567",
            "064 123 4567|+381641234567",
            "064/123-45-67|+381641234567",
            "(064) 123.45.67|+381641234567",
            "+381641234567|+381641234567",
            "+381 64 123 4567|+381641234567",
            "  +381 64 123 4567 |+381641234567",
            "00381641234567|+381641234567",
            "381641234567|+381641234567",
            "+387 65 123 456|+38765123456",
            "0038765123456|+38765123456",
            "+36 30 123 4567|+36301234567",
            "011 1234567|+381111234567",
    })
    void e164(String unos, String ocekivano) {
        assertEquals(ocekivano, Telefon.uE164(unos));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "641234567", "+0641234567", "064 123 4567 mama", "06412+34567", "+381 64", "0+381641234567",
            "+3816412345678901", "00", "0"})
    void nijeE164(String unos) {
        assertNull(Telefon.uE164(unos));
    }
}
