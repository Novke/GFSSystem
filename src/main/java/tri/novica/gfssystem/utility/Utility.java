package tri.novica.gfssystem.utility;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Utility {
    public static int index2int(String index) {
        Pattern pattern = Pattern.compile("\\d+$");  // "\\d+" matches digits, "$" ensures it's at the end of the string
        Matcher matcher = pattern.matcher(index);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        } else {
            // Handle the case where no number is found
//            throw new IllegalArgumentException("No number found in the input string: " + index);
            log.error("No number found in index: " + index);
            return Integer.MAX_VALUE;
        }
    }
}
