package tri.novica.gfssystem.utility;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tri.novica.gfssystem.exceptions.SystemException;

import static org.junit.jupiter.api.Assertions.*;

class WaLinkTest {

    static final String KANONSKI = "https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl",
            "  https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl  ",
            "http://chat.whatsapp.com/AbCdEfGhIj1234567890Kl",
            "chat.whatsapp.com/AbCdEfGhIj1234567890Kl",
            "https://www.chat.whatsapp.com/AbCdEfGhIj1234567890Kl",
            "HTTPS://Chat.WhatsApp.com/AbCdEfGhIj1234567890Kl",
            "https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl/",
            "https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl?mode=ems_copy_t",
            "https://chat.whatsapp.com/invite/AbCdEfGhIj1234567890Kl",
    })
    void prihvataOblikeIzWhatsAppaIVracaKanonski(String link) {
        assertEquals(KANONSKI, WaLink.normalizuj(link));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void praznoUklanjaLink(String link) {
        assertNull(WaLink.normalizuj(link));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "https://evil.example/chat.whatsapp.com/AbCdEfGhIj1234567890Kl",
            "https://chat.whatsapp.com.evil.example/AbCdEfGhIj1234567890Kl",
            "https://chat.whatsapp.com/kratak",
            "https://chat.whatsapp.com/Ab-Cd_EfGhIj1234567890",
            "https://wa.me/381641234567",
            "https://whatsapp.com/channel/0029VaAbCdEfGhIj1234567890",
            "https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl/drugo",
            "ftp://chat.whatsapp.com/AbCdEfGhIj1234567890Kl",
    })
    void odbijaSveStoNijePozivUGrupu(String link) {
        SystemException ex = assertThrows(SystemException.class, () -> WaLink.normalizuj(link));
        assertEquals(400, ex.getCode());
        assertEquals(WaLink.NEISPRAVAN, ex.getMessage());
    }
}
