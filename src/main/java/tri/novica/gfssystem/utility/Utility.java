package tri.novica.gfssystem.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utility {
    public static int index2int(String index) {
        Pattern pattern = Pattern.compile("\\d+$");  // "\\d+" matches digits, "$" ensures it's at the end of the string
        Matcher matcher = pattern.matcher(index);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        } else {
            // Handle the case where no number is found
            throw new IllegalArgumentException("No number found in the input string: " + index);
        }
    }
}
