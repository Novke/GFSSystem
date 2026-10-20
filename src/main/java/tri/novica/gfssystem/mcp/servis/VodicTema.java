package tri.novica.gfssystem.mcp.servis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Tema alata {@code vodic} (spec MCP 4.2): tekst je u {@code classpath:mcp/vodic-<tema>.md}, pa stiže sa deploy-em, a skill
 * ostaje tanak. A2 dodaje {@code PLATNO}. Svaki vodič ima najviše {@link #MAX_ZNAKOVA} znakova (oko 4 000 tokena; odgovor
 * alata sme do {@code McpOdgovor.MAX_ZNAKOVA}).
 */
public enum VodicTema {
    BANKA, PITANJA, PRAVILA;

    public static final int MAX_ZNAKOVA = 16_000;
    static final String NEPOZNATA = "Nepoznata tema vodiča.";

    /** Tema po imenu malim slovima ({@code banka}, {@code pitanja}, {@code pravila}); sve drugo je 400 {@value #NEPOZNATA}. */
    public static VodicTema iz(String tema) {
        for (VodicTema t : values()) {
            if (t.ime().equals(tema)) {
                return t;
            }
        }
        throw new SystemException(NEPOZNATA, HttpStatus.BAD_REQUEST);
    }

    public String resurs() {
        return "mcp/vodic-" + ime() + ".md";
    }

    /**
     * Tekst vodiča kao UTF-8 (neispravan UTF-8, nečitljiv resurs ili tekst duži od {@link #MAX_ZNAKOVA} je
     * {@link IllegalStateException}: {@code McpKontekst} ih čita pri startu, pa takav deploy ne prolazi tiho).
     */
    public String ucitaj() {
        String tekst;
        try (InputStream ulaz = new ClassPathResource(resurs()).getInputStream()) {
            tekst = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(ulaz.readAllBytes())).toString().strip();
        } catch (CharacterCodingException e) {
            throw new IllegalStateException("Vodič " + resurs() + " nije ispravan UTF-8.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Vodič " + resurs() + " nije čitljiv.", e);
        }
        if (tekst.isEmpty() || tekst.length() > MAX_ZNAKOVA) {
            throw new IllegalStateException("Vodič " + resurs() + " ima " + tekst.length() + " znakova (1 do "
                    + MAX_ZNAKOVA + ").");
        }
        return tekst;
    }

    private String ime() {
        return name().toLowerCase(Locale.ROOT);
    }
}
