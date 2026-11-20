package tri.novica.gfssystem.service.fajlovi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.fajlovi.FajlKratko;
import tri.novica.gfssystem.dto.fajlovi.FajloviSlajda;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.repository.fajlovi.FajloviStanjaRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Fajlovi u uživo stanju (spec fajlova, odeljak 5): fajlovi trenutnog slajda plus fajlovi bez slajda, ali samo dok izvođenje
 * ima važeći kod. Bez fajlova ili bez koda: prazno ({@code null} u nastavničkom, {@code []} u javnom stanju). Stanje se šalje
 * često, pa ovo košta najviše dva indeksirana upita (kod se traži samo kad fajlovi postoje).
 */
@Service
@Transactional(readOnly = true)
public class FajloviStanja {

    private final FajloviStanjaRepository repo;
    private final Clock clock;
    private final String host;

    public FajloviStanja(FajloviStanjaRepository repo, Clock clock, @Value("${gfs.front.url}") String frontUrl) {
        this.repo = repo;
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

    /** Fajlovi i kod za trenutni slajd izvođenja, ili prazno. */
    public Optional<FajloviSlajda> zaSlajd(Izvodjenje iz, Slajd trenutni) {
        if (trenutni == null || iz.getStatus() != StatusIzvodjenja.AKTIVNO) {
            return Optional.empty();
        }
        List<FajlPrezentacije> fajlovi = repo.zaSlajd(iz.getPrezentacija().getId(), trenutni.getId());
        if (fajlovi.isEmpty()) {
            return Optional.empty();
        }
        Long predavanjeId = iz.getPredavanje() == null ? null : iz.getPredavanje().getId();
        return repo.vazeciKod(predavanjeId, iz.getId(), LocalDateTime.now(clock))
                .map(kod -> new FajloviSlajda(kod, host + "/f/" + kod,
                        fajlovi.stream().map(FajlMapper::kratko).toList()));
    }
}
