package tri.novica.gfssystem.dto.fajlovi;

/** Fajl na spisku koda (javna stranica {@code /f/<kod>}); {@code prezentacija} je naziv prezentacije kojoj pripada. */
public record FajlCasa(Long id, String naziv, String vrsta, long velicina, String prezentacija) {
}
