package tri.novica.gfssystem.utility;

import lombok.extern.slf4j.Slf4j;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Utility {
    public static int index2int(String index) {
        Pattern pattern = Pattern.compile("\\d+$");  // "\\d+" matches digits, "$" ensures it's at the end of the string
        Matcher matcher = pattern.matcher(index);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                // npr. broj telefona upisan kao indeks: ne sme da obori sortiranje (i stranicu grupe)
                log.error("Number too large in index: " + index);
                return Integer.MAX_VALUE;
            }
        } else {
            // Handle the case where no number is found
//            throw new IllegalArgumentException("No number found in the input string: " + index);
            log.error("No number found in index: " + index);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Sme li student na nastavu (test, predavanje) grupe: iz te grupe, ili iz starije generacije (ponavlja predmet,
     * manja godina upisa). Student bez grupe nikad; grupa bez godine upisa samo svoje studente.
     */
    public static boolean smeNaNastavuGrupe(Student student, Grupa grupa) {
        if (student == null || grupa == null) return false;
        Grupa svoja = student.getGrupa();
        if (svoja == null) return false;
        if (svoja.getId() != null && svoja.getId().equals(grupa.getId())) return true;
        return svoja.getGodinaUpisa() != null && grupa.getGodinaUpisa() != null
                && svoja.getGodinaUpisa() < grupa.getGodinaUpisa();
    }
}
