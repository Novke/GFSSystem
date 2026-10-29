package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.entity.prijava.KljucAplikacije;
import tri.novica.gfssystem.repository.prijava.KljucAplikacijeRepository;
import tri.novica.gfssystem.service.prijava.KodServis;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * VAPID par ključeva aplikacije (spec aplikacije 5.1) u {@code kljucevi_aplikacije}: {@value #JAVNI} (nekompresovana P-256
 * tačka, 65 bajtova) i {@value #PRIVATNI} (skalar, 32 bajta), oba base64url bez dopune. Par se pravi pri prvoj upotrebi,
 * oba reda u jednoj transakciji i pod MySQL bravom {@code gfs-vapid} (sa prefiksom šeme), pa dva starta ili dve niti nikad
 * ne naprave dva para; posle se samo čita i pamti u memoriji. Učitan par se proverava potpisom (ručno izmenjen ili
 * polovičan par ruši slanje, ne tiho). Promena ključa poništava sve pretplate, pa se nikad ne rotira sam. Privatni ključ
 * se nikad ne loguje i ne napušta backend.
 */
@Service
@Slf4j
public class PushKljucevi {

    public static final String JAVNI = "VAPID_JAVNI";
    public static final String PRIVATNI = "VAPID_PRIVATNI";
    static final String BRAVA = "gfs-vapid";
    static final int CEKANJE_BRAVE_S = 10;

    /** Učitan par: javni ključ kao ga dobija browser i privatni za potpis. */
    record Par(String javniB64, ECPublicKey javni, ECPrivateKey privatni) {
    }

    private final KljucAplikacijeRepository repository;
    private final KodServis brave;
    private final TransactionTemplate novaTransakcija;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private volatile Par par;

    public PushKljucevi(KljucAplikacijeRepository repository, KodServis brave, PlatformTransactionManager transakcije,
                        Clock clock) {
        this.repository = repository;
        this.brave = brave;
        this.novaTransakcija = new TransactionTemplate(transakcije);
        this.novaTransakcija.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.novaTransakcija.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clock = clock;
    }

    /** Javni VAPID ključ (base64url, 65 bajtova nekompresovano) za {@code PushManager.subscribe}. */
    public String javniKljuc() {
        return par().javniB64();
    }

    ECPrivateKey privatniKljuc() {
        return par().privatni();
    }

    Par par() {
        Par p = par;
        if (p == null) {
            synchronized (this) {
                p = par;
                if (p == null) {
                    p = procitajIliNapravi();
                    par = p;
                }
            }
        }
        return p;
    }

    private Par procitajIliNapravi() {
        Par p = novaTransakcija.execute(s -> {
            Optional<Par> postojeci = procitaj();
            if (postojeci.isPresent()) {
                return postojeci.get();
            }
            if (!brave.uzmiBravu(BRAVA, CEKANJE_BRAVE_S, "vapid")) {
                throw new IllegalStateException("VAPID ključ nije napravljen: brava nije dobijena");
            }
            // pod bravom: možda ga je druga instanca upravo napravila
            postojeci = procitaj();
            if (postojeci.isPresent()) {
                return postojeci.get();
            }
            KeyPair nov = P256.nov(random);
            ECPublicKey javni = (ECPublicKey) nov.getPublic();
            ECPrivateKey privatni = (ECPrivateKey) nov.getPrivate();
            String javniB64 = Base64Url.kodiraj(P256.tacka(javni));
            LocalDateTime sada = LocalDateTime.now(clock);
            repository.save(red(JAVNI, javniB64, sada));
            repository.save(red(PRIVATNI, Base64Url.kodiraj(P256.skalar(privatni)), sada));
            repository.flush();
            log.info("Napravljen VAPID par ključeva aplikacije");
            return new Par(javniB64, javni, privatni);
        });
        if (p == null) {
            throw new IllegalStateException("VAPID ključ nije učitan");
        }
        return p;
    }

    private Optional<Par> procitaj() {
        Optional<KljucAplikacije> j = repository.findByNaziv(JAVNI);
        Optional<KljucAplikacije> pr = repository.findByNaziv(PRIVATNI);
        if (j.isEmpty() && pr.isEmpty()) {
            return Optional.empty();
        }
        if (j.isEmpty() || pr.isEmpty()) {
            throw new IllegalStateException("VAPID ključ u bazi nije potpun (postoji samo jedan od dva reda)");
        }
        byte[] tacka = Base64Url.dekodiraj(j.get().getVrednost());
        byte[] skalar = Base64Url.dekodiraj(pr.get().getVrednost());
        ECPublicKey javni;
        ECPrivateKey privatni;
        try {
            javni = P256.javni(tacka);
            privatni = P256.privatni(skalar);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("VAPID ključ u bazi nije ispravan");
        }
        if (!par(javni, privatni)) {
            throw new IllegalStateException("VAPID javni i privatni ključ u bazi nisu par");
        }
        return Optional.of(new Par(Base64Url.kodiraj(tacka), javni, privatni));
    }

    /** Potpis privatnim pa provera javnim: da li su to dva dela istog para. */
    static boolean par(ECPublicKey javni, ECPrivateKey privatni) {
        try {
            byte[] poruka = "gfs-vapid-provera".getBytes(StandardCharsets.US_ASCII);
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(privatni);
            s.update(poruka);
            byte[] potpis = s.sign();
            Signature v = Signature.getInstance("SHA256withECDSA");
            v.initVerify(javni);
            v.update(poruka);
            return v.verify(potpis);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static KljucAplikacije red(String naziv, String vrednost, LocalDateTime sada) {
        KljucAplikacije k = new KljucAplikacije();
        k.setNaziv(naziv);
        k.setVrednost(vrednost);
        k.setKreirano(sada);
        return k;
    }
}
