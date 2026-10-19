package tri.novica.gfssystem.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Greške bezbednosnog lanca (pre Spring MVC-a, pa bez {@code ApiExceptionHandler}-a) u istom obliku {@code {reason, time}}
 * koji front već mapira u {@code ApiError}. Vreme je ISO tekst kao u ostalim odgovorima.
 */
public final class JsonGreske {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private JsonGreske() {}

    public static void posalji(HttpServletResponse res, int status, String reason) throws IOException {
        Map<String, String> telo = new LinkedHashMap<>();
        telo.put("reason", reason);
        telo.put("time", LocalDateTime.now().toString());
        res.setStatus(status);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(JSON.writeValueAsString(telo));
        res.getWriter().flush();
    }
}
