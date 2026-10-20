package tri.novica.gfssystem.mcp.servis;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Sloj PLATNO slajda u sažetku ({@link PlatnoPregled#slojevi}): id, tip ({@code TEKST}, {@code SLIKA}, {@code OBLIK},
 * {@code TABELA}, {@code FORMULA}, {@code CRTEZ}), naziv i {@code okvir} {@code [x, y, sirina, visina]} na platnu
 * 960 x 540, pa po tipu: TEKST {@code tekst} sa preovlađujućim {@code font} i {@code velicina}, TABELA {@code tabela}
 * (redovi ćelija), FORMULA {@code latex}, SLIKA i CRTEZ {@code medijId}, CRTEZ {@code brojTacaka}, OBLIK {@code oblik}.
 * Potezi crteža se nikad ne vraćaju; odsutna polja se ne pišu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpSlojSazetak(String id, String tip, String naziv, double[] okvir, String tekst, String font,
                             Double velicina, List<List<String>> tabela, String latex, String medijId, Integer brojTacaka,
                             String oblik) {
}
