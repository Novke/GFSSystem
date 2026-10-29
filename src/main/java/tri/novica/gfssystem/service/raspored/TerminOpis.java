package tri.novica.gfssystem.service.raspored;

import tri.novica.gfssystem.entity.raspored.RasporedStavka;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Jedan termin za tekst (obaveštenje i mejl nastavniku, mejl studentu): datum, vreme, predmet i grupa iz baze. Nikad ne nosi
 * ništa što je student ukucao.
 */
public record TerminOpis(Long stavkaId, LocalDate datum, LocalTime pocetak, LocalTime kraj, String predmet, String grupa) {

    private static final String[] DANI = {"ponedeljak", "utorak", "sreda", "četvrtak", "petak", "subota", "nedelja"};
    private static final String[] U_DAN = {"u ponedeljak", "u utorak", "u sredu", "u četvrtak", "u petak", "u subotu",
            "u nedelju"};

    public static TerminOpis od(RasporedStavka s, LocalDate datum) {
        return new TerminOpis(s.getId(), datum, s.getPocetak(), s.getKraj(), s.getPredmet().getNaziv(),
                s.getGrupa().getNaziv());
    }

    /** {@code sreda} */
    public String dan() {
        return DANI[datum.getDayOfWeek().getValue() - 1];
    }

    /** {@code u sredu} */
    public String uDan() {
        return U_DAN[datum.getDayOfWeek().getValue() - 1];
    }

    /** {@code 4. 11.} */
    public String datumKratko() {
        return datum.getDayOfMonth() + ". " + datum.getMonthValue() + ".";
    }

    /** {@code 4. 11. 2026.} */
    public String datumPun() {
        return datumKratko() + " " + datum.getYear() + ".";
    }

    /** {@code 10:00–13:00} */
    public String blok() {
        return hhmm(pocetak) + "–" + hhmm(kraj);
    }

    /** Ključ jedinog obaveštenja {@code MNOGO_NAJAVA} za ovaj termin ({@code obavestenja.kljuc}). */
    public String kljucObavestenja() {
        return "MNOGO_NAJAVA:" + stavkaId + ":" + datum;
    }

    public static String hhmm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    /** {@code 1 student}, {@code 2 studenta}, {@code 5 studenata}, {@code 21 student} (paukal kao u srpskom). */
    public static String studenata(long n) {
        long d = n % 10;
        long s = n % 100;
        String rec = d == 1 && s != 11 ? "student" : d >= 2 && d <= 4 && (s < 12 || s > 14) ? "studenta" : "studenata";
        return n + " " + rec;
    }
}
