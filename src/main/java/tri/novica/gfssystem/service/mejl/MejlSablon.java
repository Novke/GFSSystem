package tri.novica.gfssystem.service.mejl;

import java.util.List;

/**
 * Izgled mejlova aplikacije: isti sadržaj kao čist tekst i kao jednostavan HTML ({@code multipart/alternative}). HTML je
 * namerno skroman (tabela širine do 480 px, samo inline stilovi, bez spoljnog CSS-a, skripti, praćenja i linkova), jer
 * Gmail za mejl sa {@code @gmail.com} naloga presuđuje po sadržaju i reputaciji naloga, ne po izgledu. Logo je mala slika
 * sa javne putanje fronta ({@code assets/images/mejl-logo.png}; Gmail je preuzima kroz svoj proxy), sa {@code alt} tekstom,
 * pa mejl bez slika ostaje čitljiv. Svaki tekst koji dolazi spolja (ime studenta) se escape-uje.
 */
public final class MejlSablon {

    /** Potpis na dnu svakog mejla. */
    public static final String POTPIS = "Građevinski fakultet Subotica";
    static final String PODNASLOV = "GFS, alat za nastavu";

    private MejlSablon() {
    }

    /**
     * Sadržaj jednog mejla.
     *
     * @param pozdrav  prvi red ("Zdravo, Ana!") ili null
     * @param uvod     pasusi pre koda
     * @param kod      kod koji se ističe (krupno, lako za kopiranje) ili null
     * @param posle    pasusi posle koda
     * @param logoUrl  apsolutni https URL loga ili null (bez slike)
     */
    public record Sadrzaj(String pozdrav, List<String> uvod, String kod, List<String> posle, String logoUrl) {}

    /** Čist tekst: isti pasusi kao HTML, kod u svom redu. */
    public static String tekst(Sadrzaj s) {
        StringBuilder b = new StringBuilder();
        if (s.pozdrav() != null) {
            b.append(s.pozdrav()).append("\n\n");
        }
        for (String p : s.uvod()) {
            b.append(p).append("\n\n");
        }
        if (s.kod() != null) {
            b.append("    ").append(s.kod()).append("\n\n");
        }
        for (String p : s.posle()) {
            b.append(p).append("\n\n");
        }
        b.append("--\n").append(POTPIS).append("\n").append(PODNASLOV).append("\n");
        return b.toString();
    }

    /** HTML deo (UTF-8), sve inline. */
    public static String html(Sadrzaj s) {
        StringBuilder b = new StringBuilder(2048);
        b.append("<!DOCTYPE html><html lang=\"sr-Latn\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(esc(POTPIS)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background-color:#f4f5f7;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"background-color:#f4f5f7;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"max-width:480px;background-color:#ffffff;border:1px solid #dfe3e8;border-radius:8px;\">");
        // zaglavlje: logo + naziv
        b.append("<tr><td style=\"padding:20px 24px 12px 24px;border-bottom:1px solid #eceff3;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");
        if (s.logoUrl() != null) {
            b.append("<td style=\"padding-right:12px;vertical-align:middle;\"><img src=\"").append(esc(s.logoUrl()))
                    .append("\" width=\"33\" height=\"48\" alt=\"GFS\" style=\"display:block;border:0;\"></td>");
        }
        b.append("<td style=\"vertical-align:middle;font-family:Arial,Helvetica,sans-serif;\">")
                .append("<div style=\"font-size:15px;font-weight:bold;color:#1b2a41;\">").append(esc(POTPIS)).append("</div>")
                .append("<div style=\"font-size:13px;color:#5b6675;\">").append(esc(PODNASLOV)).append("</div>")
                .append("</td></tr></table></td></tr>");
        // telo
        b.append("<tr><td style=\"padding:20px 24px 8px 24px;font-family:Arial,Helvetica,sans-serif;font-size:15px;")
                .append("line-height:22px;color:#1b2a41;\">");
        if (s.pozdrav() != null) {
            b.append(pasus(s.pozdrav()));
        }
        for (String p : s.uvod()) {
            b.append(pasus(p));
        }
        if (s.kod() != null) {
            b.append("<div style=\"margin:20px 0;padding:14px 0;text-align:center;background-color:#f1f4f9;")
                    .append("border:1px solid #d5dce6;border-radius:6px;font-family:'Courier New',Courier,monospace;")
                    .append("font-size:32px;line-height:40px;font-weight:bold;letter-spacing:6px;color:#0f1d33;\">")
                    .append(esc(s.kod())).append("</div>");
        }
        for (String p : s.posle()) {
            b.append(pasus(p));
        }
        b.append("</td></tr>");
        // podnožje
        b.append("<tr><td style=\"padding:12px 24px 20px 24px;font-family:Arial,Helvetica,sans-serif;font-size:13px;")
                .append("line-height:18px;color:#5b6675;border-top:1px solid #eceff3;\">")
                .append(esc(POTPIS)).append("<br>").append(esc(PODNASLOV)).append("</td></tr>");
        b.append("</table></td></tr></table></body></html>");
        return b.toString();
    }

    private static String pasus(String tekst) {
        return "<p style=\"margin:0 0 14px 0;\">" + esc(tekst) + "</p>";
    }

    /** HTML escape za tekst i vrednost atributa. */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
