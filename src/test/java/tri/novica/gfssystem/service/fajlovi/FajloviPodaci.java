package tri.novica.gfssystem.service.fajlovi;

import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Pomoć za testove fajlova: prezentacije, slajdovi i fajlovi (JDBC red + pravi medij), čišćenje medija. */
public final class FajloviPodaci {

    static final byte[] PK = {0x50, 0x4B, 0x03, 0x04, 0x14, 0, 6, 0, 8, 0};

    private final JdbcTemplate jdbc;
    private final MedijService medijService;
    private final PrezentacijaService prezentacijaService;
    private final Long predmetId;
    final List<String> mediji = new ArrayList<>();
    private int rb = 1;

    public FajloviPodaci(JdbcTemplate jdbc, MedijService medijService, PrezentacijaService prezentacijaService, Long predmetId) {
        this.jdbc = jdbc;
        this.medijService = medijService;
        this.prezentacijaService = prezentacijaService;
        this.predmetId = predmetId;
    }

    public Long prezentacija(String naziv) {
        return prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmetId, naziv, null, null)).id();
    }

    public Long slajd(Long prezentacijaId, String naslov) {
        return prezentacijaService.dodajSlajd(prezentacijaId, new SlajdCmd(TipSlajda.INFO, naslov, "x", null, null, false,
                null, null, null, null), null).id();
    }

    /** Fajl uz prezentaciju ({@code slajdId} null = uz celu). */
    public Long fajl(Long prezentacijaId, Long slajdId, String naziv) {
        String medij = medijService.sacuvajFajl(PK, naziv).id();
        mediji.add(medij);
        LocalDateTime sada = LocalDateTime.now();
        jdbc.update("insert into fajlovi_prezentacije (prezentacija_id, slajd_id, medij_id, naziv, rb, kreirano, izmenjeno) "
                + "values (?, ?, ?, ?, ?, ?, ?)", prezentacijaId, slajdId, medij, naziv, rb++, sada, sada);
        return jdbc.queryForObject("select id from fajlovi_prezentacije where medij_id = ?", Long.class, medij);
    }

    public void zavrsiIzvodjenje(Long izvodjenjeId) {
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null, kraj = ? where id = ?",
                LocalDateTime.now(), izvodjenjeId);
    }

    /** Posle {@code CasPodaci.obrisi()} (redovi fajlova su otišli kaskadom): brišu se i mediji. */
    public void ocisti() {
        for (String id : mediji) medijService.obrisi(id);
    }
}
