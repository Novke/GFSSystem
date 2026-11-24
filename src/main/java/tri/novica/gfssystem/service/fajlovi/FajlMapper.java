package tri.novica.gfssystem.service.fajlovi;

import tri.novica.gfssystem.dto.fajlovi.FajlCasa;
import tri.novica.gfssystem.dto.fajlovi.FajlKratko;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeInfo;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.service.uzivo.MedijService;

/**
 * Entitet -> DTO za fajlove uz prezentaciju. Čita lazy veze (prezentacija, slajd, medij), pa se zove u transakciji ili
 * nad entitetima koje je vratio {@link PristupFajlovima} (inicijalizuje ih). {@code vrsta} dolazi iz ekstenzije imena
 * ({@link MedijService#vrstaFajla}), nikad iz zaglavlja klijenta.
 */
public final class FajlMapper {

    private FajlMapper() {
    }

    public static FajlPrezentacijeInfo info(FajlPrezentacije f) {
        return new FajlPrezentacijeInfo(f.getId(), f.getPrezentacija().getId(),
                f.getSlajd() == null ? null : f.getSlajd().getId(), f.getSlajd() == null ? null : f.getSlajd().getRb(),
                f.getNaziv(), vrsta(f), f.getMedij().getVelicina(), f.getRb(), f.getKreirano());
    }

    public static FajlKratko kratko(FajlPrezentacije f) {
        return new FajlKratko(f.getId(), f.getNaziv(), vrsta(f), f.getMedij().getVelicina());
    }

    public static FajlCasa fajlCasa(FajlPrezentacije f) {
        return new FajlCasa(f.getId(), f.getNaziv(), vrsta(f), f.getMedij().getVelicina(), f.getPrezentacija().getNaziv());
    }

    private static String vrsta(FajlPrezentacije f) {
        return MedijService.vrstaFajla(f.getNaziv());
    }
}
