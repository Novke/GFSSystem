package tri.novica.gfssystem.service.fajlovi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.fajlovi.FajlCasa;
import tri.novica.gfssystem.dto.fajlovi.FajloviCasa;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.entity.fajlovi.KodFajlova;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.prijava.PokusajiServis;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.time.Duration;
import java.util.List;

/**
 * Fajlovi na času po kratkom kodu, bez prijave (spec fajlova, odeljak 5): spisak {@code GET /public/fajlovi/{kod}} i
 * preuzimanje. Pogrešan ili nevažeći kod (i onaj koji nije četiri cifre) je 404 {@value #KOD_NE_VAZI} i pokušaj u
 * {@code pokusaji} ({@link VrstaPokusaja#KOD_FAJLOVA}, ključ = puna adresa klijenta kao u {@code KodServis}); posle
 * {@value #LIMIT} pogrešnih u {@code 15} minuta po IP-u je 429 za svaki kod, i ispravan, dok prozor ne prođe. Ispravan
 * kod ništa ne troši, pa razred iza jednog NAT-a preuzima koliko hoće. Pravilo "šta je na spisku" je u
 * {@link PristupFajlovima}, važenje koda u {@link KodoviFajlova}; ovde samo broj pokušaja i gradnja odgovora. DTO se gradi u
 * transakciji ({@code /public/fajlovi/**} je van open-in-view).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FajloviCasaServis {

    public static final String KOD_NE_VAZI = "Kod ne važi. Proveri broj sa table.";
    public static final String PREVISE_POGRESNIH = "Previše pogrešnih kodova. Pokušaj ponovo za 15 minuta.";
    public static final int LIMIT = 40;
    public static final Duration PROZOR = Duration.ofMinutes(15);

    private final KodoviFajlova kodovi;
    private final PristupFajlovima pristup;
    private final PokusajiServis pokusaji;

    /** Fajlovi važećeg koda redom iz spec-a; predmet i tema iz predavanja (ili prezentacije kad nema predavanja). */
    public FajloviCasa spisak(String kod, String ip) {
        KodFajlova k = vazeci(kod, ip);
        List<FajlCasa> fajlovi = pristup.fajloviKoda(k).stream().map(FajlMapper::fajlCasa).toList();
        if (k.getPredavanje() != null) {
            var p = k.getPredavanje();
            return new FajloviCasa(k.getAktivanKod(), p.getPredmet().getNaziv(), p.getTema(), p.getDatum(), fajlovi);
        }
        Izvodjenje iz = k.getIzvodjenje();
        return new FajloviCasa(k.getAktivanKod(), iz.getPrezentacija().getPredmet().getNaziv(), iz.getPrezentacija().getNaziv(),
                iz.getPocetak().toLocalDate(), fajlovi);
    }

    /** Fajl sa spiska koda; fajl van spiska je isto što i pogrešan kod (404 {@value #KOD_NE_VAZI}), ali ne troši pokušaj. */
    public Preuzet preuzmi(String kod, Long fajlId, String ip) {
        KodFajlova k = vazeci(kod, ip);
        FajlPrezentacije f = pristup.fajloviKoda(k).stream().filter(x -> x.getId().equals(fajlId)).findFirst()
                .orElseThrow(() -> new SystemException(KOD_NE_VAZI, HttpStatus.NOT_FOUND));
        return new Preuzet(f.getNaziv(), f.getMedij().getId());
    }

    /** Šta se preuzima: ime i id medija; bajtove učitava pozivalac ({@link MedijService#ucitaj}), van transakcije. */
    public record Preuzet(String naziv, String medijId) {
    }

    private KodFajlova vazeci(String kod, String ip) {
        if (pokusaji.dostignut(VrstaPokusaja.KOD_FAJLOVA, ip, LIMIT, PROZOR)) {
            log.info("Kod fajlova odbijen (limit po IP-u), ip={}", ip);
            throw new SystemException(PREVISE_POGRESNIH, HttpStatus.TOO_MANY_REQUESTS);
        }
        return kodovi.vazeci(kod).orElseThrow(() -> {
            pokusaji.zabelezi(VrstaPokusaja.KOD_FAJLOVA, ip);
            return new SystemException(KOD_NE_VAZI, HttpStatus.NOT_FOUND);
        });
    }
}
