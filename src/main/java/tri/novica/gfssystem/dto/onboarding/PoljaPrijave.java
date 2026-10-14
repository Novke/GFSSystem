package tri.novica.gfssystem.dto.onboarding;

import java.time.LocalDate;

public interface PoljaPrijave {
    String getIme();
    String getPrezime();
    String getIndeks();
    Integer getGodina();
    String getEmail();
    String getBrojTelefona();
    LocalDate getDatumRodjenja();
    String getOpstina();
}
