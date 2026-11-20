package tri.novica.gfssystem.service.fajlovi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.fajlovi.FajloviSlajda;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.Prikaz;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.fajlovi.FajloviStanjaRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Fajlovi u uživo stanju (spec fajlova, odeljci 5 i 6). Dok izvođenje ima važeći kod i prezentacija ima bar jedan fajl,
 * stanje nosi kod i link ({@link FajloviSlajda}) sa fajlovima trenutnog mesta: na slajdu njegovi fajlovi plus fajlovi
 * bez slajda, na prijavi samo fajlovi bez slajda (spisak može biti prazan: link je tu za kasnioce, taster {@code K}).
 * Bez fajlova, bez koda i na kraju: prazno ({@code null} u nastavničkom, {@code []} u javnom stanju).
 * <p>
 * Traka sa linkom na projektoru ({@link Trenutno#naTabli()}) ne stoji stalno: sama se pali na slajdu koji ima svoje
 * fajlove, a fajlovi uz celu prezentaciju je pale samo na prijavi i na prvom slajdu (dok se studenti okupljaju).
 * Nastavnik je na trenutnom mestu pali ili gasi tasterom {@code K} ({@link #prebaciTablu}, {@link FajloviTablaRegistar}).
 * Stanje se šalje često, pa ovo košta najviše dva indeksirana upita (kod se traži samo kad fajlovi postoje).
 */
@Service
@Transactional(readOnly = true)
public class FajloviStanja {

    public static final String NEMA_FAJLOVA = "Uz ovu prezentaciju nema fajlova za studente.";

    /** Fajlovi trenutnog mesta izvođenja i da li link ide na projektor. */
    public record Trenutno(FajloviSlajda fajlovi, boolean naTabli) {
    }

    private final FajloviStanjaRepository repo;
    private final FajloviTablaRegistar tabla;
    private final Clock clock;
    private final String host;

    public FajloviStanja(FajloviStanjaRepository repo, FajloviTablaRegistar tabla, Clock clock,
                         @Value("${gfs.front.url}") String frontUrl) {
        this.repo = repo;
        this.tabla = tabla;
        this.clock = clock;
        this.host = host(frontUrl);
    }

    /** Prvi unos {@code gfs.front.url} bez šeme i završne kose crte ({@code https://gfs.trif.rs/} -> {@code gfs.trif.rs}). */
    static String host(String frontUrl) {
        String prvi = frontUrl.split(",")[0].strip();
        int sema = prvi.indexOf("://");
        if (sema >= 0) prvi = prvi.substring(sema + 3);
        while (prvi.endsWith("/")) prvi = prvi.substring(0, prvi.length() - 1);
        return prvi;
    }

    /**
     * Fajlovi, kod i traka za trenutno mesto izvođenja, ili prazno. {@code trenutni} je slajd na ekranu ({@code null} na
     * prijavi i na kraju), {@code indeks} njegov redni broj od 0 (-1 prijava).
     */
    public Optional<Trenutno> trenutno(Izvodjenje iz, Slajd trenutni, int indeks) {
        if (iz.getStatus() != StatusIzvodjenja.AKTIVNO) {
            return Optional.empty();
        }
        boolean prijava = iz.getPrikaz() == Prikaz.PRIJAVA;
        if (!prijava && (iz.getPrikaz() != Prikaz.SLAJD || trenutni == null)) {
            return Optional.empty();
        }
        List<FajlPrezentacije> svi = repo.zaPrezentaciju(iz.getPrezentacija().getId());
        if (svi.isEmpty()) {
            return Optional.empty();
        }
        Long predavanjeId = iz.getPredavanje() == null ? null : iz.getPredavanje().getId();
        Optional<String> kod = repo.vazeciKod(predavanjeId, iz.getId(), LocalDateTime.now(clock));
        if (kod.isEmpty()) {
            return Optional.empty();
        }
        Long slajdId = prijava ? null : trenutni.getId();
        List<FajlPrezentacije> ovde = svi.stream()
                .filter(f -> f.getSlajd() == null || (slajdId != null && slajdId.equals(f.getSlajd().getId())))
                .toList();
        boolean svoji = ovde.stream().anyMatch(f -> f.getSlajd() != null);
        boolean zaCelu = ovde.stream().anyMatch(f -> f.getSlajd() == null);
        boolean samo = svoji || (zaCelu && (prijava || indeks == 0));
        boolean naTabli = tabla.prikazan(iz.getId(), slajdId).orElse(samo);
        return Optional.of(new Trenutno(new FajloviSlajda(kod.get(), host + "/f/" + kod.get(),
                ovde.stream().map(FajlMapper::kratko).toList()), naTabli));
    }

    /**
     * Taster {@code K}: traka na trenutnom mestu prelazi u suprotno stanje (i kad je bila tu po pravilu, pa nastavnik
     * može i da je skloni). 409 {@link #NEMA_FAJLOVA} kad ovde nema linka (prezentacija bez fajlova, bez koda, kraj).
     * Poziva se pod zaključanim redom izvođenja.
     */
    public void prebaciTablu(Izvodjenje iz, Slajd trenutni, int indeks) {
        Trenutno t = trenutno(iz, trenutni, indeks)
                .orElseThrow(() -> new SystemException(NEMA_FAJLOVA, HttpStatus.CONFLICT));
        tabla.postavi(iz.getId(), iz.getPrikaz() == Prikaz.PRIJAVA ? null : trenutni.getId(), !t.naTabli());
    }

    /** Izvođenje je otišlo sa ovog mesta (drugi slajd, prijava, kraj): ručni prekidač više ne važi. */
    public void zaboravi(Long izvodjenjeId) {
        tabla.ukloni(izvodjenjeId);
    }
}
