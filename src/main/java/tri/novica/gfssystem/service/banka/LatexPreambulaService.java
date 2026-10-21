package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.banka.PreambulaCmd;
import tri.novica.gfssystem.dto.banka.PreambulaInfo;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * LaTeX preambula predmeta (spec banke 3.3), ulaz rendera svake njegove varijante. Pravila: tekst bez razmaka na
 * krajevima, prazan = {@code null} = {@link LatexDokument#PODRAZUMEVANA_PREAMBULA} (i tekst jednak podrazumevanoj se
 * čuva kao {@code null}: heš je isti, pa keš ne treba poništavati), najviše {@value #MAX_ZNAKOVA} znakova (code point-a),
 * deny-lista za preambulu ({@link LatexPravila#zabranjenaKomandaUPreambuli}, sme pakete i katkodove), pa <b>probni
 * render</b> ({@link RenderVarijante#proveriPreambulu}: kratko telo sa novom preambulom) pre čuvanja: preambula koja se ne
 * prevodi je 400 {@code Preambula se ne kompajlira (linija N): <poruka>} (linija u preambuli; bez nje kad greška nije u
 * preambuli), render servis koji ne radi 503; u oba slučaja se ništa ne upisuje. Povratak na podrazumevanu se ne
 * renderuje (poznata je i ispravna, radi i dok servis ne radi).
 * <p>
 * Promena vrednosti poništava render keš cele banke predmeta ({@code VarijantaRepository.ponistiHashPredmeta}: heš je
 * funkcija preambule), pa varijante postaju zastarele i ponovo se renderuju kroz {@code VarijantaService.renderujSve}.
 * Ista vrednost ne dira ništa (ni red predmeta ni keš). Poništavanje je u istoj transakciji, posle upisa preambule i
 * pre povratka; transakcija ne učitava varijante, pa u persistence context-u nema starih entiteta koje bulk update ne bi
 * osvežio. Zastareli heš se uvek prepoznaje kao zastareo (heš sadrži preambulu iz koje je nastao): render koji je počeo sa
 * starom preambulom može da upiše stari heš, ali ga {@code VarijantaService} pod ključem poredi sa trenutnom
 * preambulom, a {@code renderuj-sve} svaku takvu varijantu renderuje ponovo.
 * <p>
 * <b>Render pre ključa, pa zaključavanje:</b> {@link #postavi} je bez transakcije (probni render traje do 30 s, bez ključa i
 * transakcije, kao u {@code VarijantaService}); trenutna vrednost se čita u kratkoj transakciji, a upis ide u kratkoj transakciji koja počinje praznim
 * persistence context-om (open-in-view), zaključa red predmeta ({@code PredmetRepository.findByIdForUpdate}, prvi u
 * redosledu predmet -> grupa -> varijanta iz {@link GrupaZadatakaService}), ponovo poredi vrednost i tek onda upisuje.
 * Bulk update zatim zaključa (X) redove varijanti predmeta; redove grupa čita kroz sekundarni indeks
 * {@code idx_grupe_zadataka_stablo} (deljeni ključ na tom indeksu, ne na redu grupe), pa ne čeka nikog ko drži red grupe
 * ({@code findByIdForUpdate} zaključava primarni ključ). Ciklusa nema: {@link GrupaZadatakaService} uzima red predmeta pre
 * svega ostalog (čeka ovaj servis ne držeći ništa), a {@code VarijantaService} drži najviše jednu grupu i jednu varijantu i
 * ne traži ništa što ovaj servis drži osim te varijante. READ COMMITTED kao ostali servisi banke.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class LatexPreambulaService {

    static final int MAX_ZNAKOVA = 50_000;
    static final String PREAMBULA_PREDUGACKA = "Preambula može imati najviše 50 000 znakova.";
    static final String NE_KOMPAJLIRA = "Preambula se ne kompajlira";

    private final PredmetRepository predmetRepository;
    private final VarijantaRepository varijantaRepository;
    private final RenderVarijante renderVarijante;
    private final PlatformTransactionManager transakcije;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public PreambulaInfo preambula(Long predmetId) {
        return info(predmetRepository.findById(predmetId).orElseThrow(LatexPreambulaService::predmetNijePronadjen)
                .getLatexPreambula());
    }

    /**
     * Postavlja preambulu ({@code null}, prazna ili jednaka podrazumevanoj = podrazumevana); vraća stanje posle izmene.
     * Nova vrednost (osim podrazumevane) prvo prolazi probni render, van transakcije i bez ključa.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PreambulaInfo postavi(Long predmetId, PreambulaCmd cmd) {
        String nova = normalizuj(cmd.preambula());
        proveri(nova);
        String trenutna = transakcija(true, () -> predmetRepository.findById(predmetId)
                .orElseThrow(LatexPreambulaService::predmetNijePronadjen).getLatexPreambula());
        if (Objects.equals(nova, normalizuj(trenutna))) {
            return info(trenutna);
        }
        if (nova != null) {
            renderVarijante.proveriPreambulu(nova).ifPresent(LatexPreambulaService::neKompajlira);
        }
        return transakcija(false, () -> {
            Predmet predmet = predmetRepository.findByIdForUpdate(predmetId).orElseThrow(LatexPreambulaService::predmetNijePronadjen);
            if (Objects.equals(nova, normalizuj(predmet.getLatexPreambula()))) {
                return info(predmet.getLatexPreambula());   // ista vrednost je upisana u međuvremenu
            }
            predmet.setLatexPreambula(nova);
            predmetRepository.save(predmet);
            int varijanti = varijantaRepository.ponistiHashPredmeta(predmetId);
            log.info("Preambula predmeta {} izmenjena ({}), keš rendera poništen za {} varijanti", predmetId,
                    nova == null ? "podrazumevana" : nova.length() + " znakova", varijanti);
            return info(nova);
        });
    }

    private static void proveri(String preambula) {
        if (preambula == null) return;
        if (preambula.codePointCount(0, preambula.length()) > MAX_ZNAKOVA) {
            throw new SystemException(PREAMBULA_PREDUGACKA, HttpStatus.BAD_REQUEST);
        }
        LatexPravila.zabranjenaKomandaUPreambuli(preambula).ifPresent(komanda -> {
            throw new SystemException("Komanda " + komanda + " nije dozvoljena u preambuli.", HttpStatus.BAD_REQUEST);
        });
    }

    private static void neKompajlira(RenderGreskaInfo g) {
        throw new SystemException(NE_KOMPAJLIRA + (g.linija() == null ? "" : " (linija " + g.linija() + ")") + ": " + g.poruka(),
                HttpStatus.BAD_REQUEST);
    }

    /** Bez razmaka na krajevima; prazno i tekst jednak podrazumevanoj (i sa CRLF) su {@code null}. */
    private static String normalizuj(String preambula) {
        if (preambula == null) return null;
        String s = preambula.strip();
        if (s.isEmpty() || s.replace("\r\n", "\n").replace('\r', '\n').equals(LatexDokument.PODRAZUMEVANA_PREAMBULA)) {
            return null;
        }
        return s;
    }

    private static PreambulaInfo info(String preambula) {
        String upisana = normalizuj(preambula);
        return new PreambulaInfo(upisana, upisana == null, LatexDokument.PODRAZUMEVANA_PREAMBULA);
    }

    /** Kratka transakcija (READ COMMITTED) sa praznim persistence context-om: uz open-in-view ključ inače vraća stari entitet. */
    private <T> T transakcija(boolean samoCitanje, Supplier<T> posao) {
        TransactionTemplate tx = new TransactionTemplate(transakcije);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setReadOnly(samoCitanje);
        return tx.execute(status -> {
            em.clear();
            return posao.get();
        });
    }

    private static SystemException predmetNijePronadjen() {
        return new SystemException(GrupaZadatakaService.PREDMET_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
    }
}
