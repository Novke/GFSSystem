package tri.novica.gfssystem.dto.banka;

/**
 * Ishod render-probe: SVG zadatka i rešenja kao tekst (UTF-8, ne base64) kad je {@code ok}, inače greška i deo u kome
 * je ({@code "tekst"} ili {@code "resenje"}, linija relativna na taj deo). Kad je pao samo deo {@code resenje}, {@code svg}
 * nosi zadatak koji se preveo ({@code resenjeSvg} je {@code null}); kad je pao {@code tekst}, nema nijednog SVG-a.
 */
public record RenderProbaInfo(boolean ok, String svg, String resenjeSvg, RenderGreskaInfo greska, String deo) {
}
