package tri.novica.gfssystem.service.lti;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.lti.LtiRegistracijaCmd;
import tri.novica.gfssystem.dto.lti.LtiRegistracijaInfo;
import tri.novica.gfssystem.dto.lti.LtiVrednostiZaMoodleInfo;
import tri.novica.gfssystem.entity.lti.LtiRegistracija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.lti.LtiRegistracijaRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Registracije LTI 1.3 platformi (spec 8.1): nastavnik ih vodi u aplikaciji, jer svaki predmet ima svoj Client ID i
 * Deployment ID, a nov predmet ne traži deploy. Servis vraća samo DTO-ove (open-in-view je isključen za LTI putanje).
 */
@Service
@Slf4j
public class LtiRegistracijaServis {

    static final String NE_POSTOJI = "Registracija nije pronađena.";
    static final String DUPLIKAT = "Registracija za ovaj Moodle, Client ID i Deployment ID već postoji.";
    static final String AUTH_PUTANJA = "/mod/lti/auth.php";
    static final String CERTS_PUTANJA = "/mod/lti/certs.php";

    private final LtiRegistracijaRepository repo;
    private final Clock clock;
    private final String frontUrl;
    private final boolean dozvoliHttp;

    public LtiRegistracijaServis(LtiRegistracijaRepository repo, Clock clock,
                                 @Value("${gfs.front.url}") String frontUrl,
                                 @Value("${gfs.lti.dozvoli-http:false}") boolean dozvoliHttp) {
        this.repo = repo;
        this.clock = clock;
        this.frontUrl = frontUrl;
        this.dozvoliHttp = dozvoliHttp;
    }

    @Transactional(readOnly = true)
    public List<LtiRegistracijaInfo> lista() {
        return repo.findAll().stream().map(LtiRegistracijaServis::uInfo).toList();
    }

    @Transactional
    public LtiRegistracijaInfo napravi(LtiRegistracijaCmd cmd) {
        LtiRegistracija r = normalizuj(cmd);
        r.setKreirano(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
        proveriDuplikat(r, null);
        return uInfo(sacuvaj(r));
    }

    @Transactional
    public LtiRegistracijaInfo izmeni(Long id, LtiRegistracijaCmd cmd) {
        LtiRegistracija r = repo.findById(id).orElseThrow(() -> new SystemException(NE_POSTOJI, 404));
        // duplikat se traži pre izmene upravljanog reda: upit bi inače ispraznio izmenu i pao na UNIQUE ključu (500)
        LtiRegistracija nova = normalizuj(cmd);
        proveriDuplikat(nova, id);
        r.setNaziv(nova.getNaziv());
        r.setIzdavac(nova.getIzdavac());
        r.setClientId(nova.getClientId());
        r.setDeploymentId(nova.getDeploymentId());
        r.setAuthUrl(nova.getAuthUrl());
        r.setJwksUrl(nova.getJwksUrl());
        r.setAktivna(nova.isAktivna());
        return uInfo(sacuvaj(r));
    }

    @Transactional
    public void obrisi(Long id) {
        LtiRegistracija r = repo.findById(id).orElseThrow(() -> new SystemException(NE_POSTOJI, 404));
        repo.delete(r);
        repo.flush();
        log.info("LTI: obrisana registracija id={}", id);
    }

    /** Vrednosti za Moodle formu: host je prvi unos {@code gfs.front.url} (lista je odvojena zarezom). */
    public LtiVrednostiZaMoodleInfo vrednosti() {
        String osnova = frontUrl.split(",")[0].trim();
        while (osnova.endsWith("/")) osnova = osnova.substring(0, osnova.length() - 1);
        String start = osnova + "/api/public/lti/start";
        return new LtiVrednostiZaMoodleInfo(start, osnova + "/api/public/lti/prijava", start,
                osnova + "/api/public/lti/jwks");
    }

    /** Proverena i očišćena polja u novom (neupravljanom) entitetu; {@code id} i {@code kreirano} se ne diraju. */
    private LtiRegistracija normalizuj(LtiRegistracijaCmd cmd) {
        LtiRegistracija r = new LtiRegistracija();
        String izdavac = LtiUrl.proveri(cmd.izdavac(), dozvoliHttp, "Izdavač (issuer)");
        r.setNaziv(cmd.naziv().trim());
        r.setIzdavac(izdavac);
        r.setClientId(cmd.clientId().trim());
        r.setDeploymentId(cmd.deploymentId().trim());
        r.setAuthUrl(LtiUrl.proveri(prazno(cmd.authUrl()) ? LtiUrl.izvedi(izdavac, AUTH_PUTANJA) : cmd.authUrl(),
                dozvoliHttp, "Auth URL"));
        r.setJwksUrl(LtiUrl.proveri(prazno(cmd.jwksUrl()) ? LtiUrl.izvedi(izdavac, CERTS_PUTANJA) : cmd.jwksUrl(),
                dozvoliHttp, "JWKS URL"));
        r.setAktivna(cmd.aktivna());
        if (r.getAuthUrl().length() > 500 || r.getJwksUrl().length() > 500) {
            throw new SystemException("Izvedena adresa je predugačka.", 400);
        }
        return r;
    }

    private static boolean prazno(String s) {
        return s == null || s.isBlank();
    }

    private void proveriDuplikat(LtiRegistracija r, Long samId) {
        Optional<LtiRegistracija> postoji = repo.findByIzdavacAndClientIdAndDeploymentId(r.getIzdavac(),
                r.getClientId(), r.getDeploymentId());
        if (postoji.isPresent() && !postoji.get().getId().equals(samId)) {
            throw new SystemException(DUPLIKAT, 409);
        }
    }

    /** Upis sa flush-om u istoj transakciji: istovremeni duplikat pada na UNIQUE ključu i postaje 409. */
    private LtiRegistracija sacuvaj(LtiRegistracija r) {
        try {
            return repo.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw new SystemException(DUPLIKAT, 409);
        }
    }

    static LtiRegistracijaInfo uInfo(LtiRegistracija r) {
        return new LtiRegistracijaInfo(r.getId(), r.getNaziv(), r.getIzdavac(), r.getClientId(), r.getDeploymentId(),
                r.getAuthUrl(), r.getJwksUrl(), r.isAktivna(), r.getKreirano());
    }
}
