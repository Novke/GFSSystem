package tri.novica.gfssystem.service.banka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.PreambulaCmd;
import tri.novica.gfssystem.dto.banka.PreambulaInfo;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;

import java.util.Objects;

/**
 * LaTeX preambula predmeta (spec banke 3.3), ulaz rendera svake njegove varijante. Pravila: tekst bez razmaka na
 * krajevima, prazan = {@code null} = {@link LatexDokument#PODRAZUMEVANA_PREAMBULA}, najviše {@value #MAX_ZNAKOVA} znakova
 * (code point-a), deny-lista za preambulu ({@link LatexPravila#zabranjenaKomandaUPreambuli}, sme pakete i katkodove).
 * <p>
 * Promena vrednosti poništava render keš cele banke predmeta ({@code VarijantaRepository.ponistiHashPredmeta}: heš je
 * funkcija preambule), pa varijante postaju zastarele i ponovo se renderuju kroz {@code VarijantaService.renderujSve}.
 * Ista vrednost ne dira ništa (ni red predmeta ni keš). Poništavanje je u istoj transakciji, posle upisa preambule i
 * pre povratka; metoda ne učitava varijante, pa u persistence context-u nema starih entiteta koje bulk update ne bi
 * osvežio. Zastareli heš se uvek prepoznaje kao zastareo (heš sadrži preambulu iz koje je nastao): render koji je počeo sa
 * starom preambulom može da upiše stari heš, ali ga {@code VarijantaService} pod ključem poredi sa trenutnom
 * preambulom, a {@code renderuj-sve} svaku takvu varijantu renderuje ponovo.
 * <p>
 * <b>Zaključavanje:</b> red predmeta ({@code PredmetRepository.findByIdForUpdate}), prvi u redosledu predmet -> grupa ->
 * varijanta iz {@link GrupaZadatakaService}; izmena stabla i promena preambule istog predmeta idu redom. Ništa drugo se ne
 * zaključava. READ COMMITTED kao ostali servisi banke.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class LatexPreambulaService {

    static final int MAX_ZNAKOVA = 50_000;
    static final String PREAMBULA_PREDUGACKA = "Preambula može imati najviše 50 000 znakova.";

    private final PredmetRepository predmetRepository;
    private final VarijantaRepository varijantaRepository;

    @Transactional(readOnly = true)
    public PreambulaInfo preambula(Long predmetId) {
        return info(predmetRepository.findById(predmetId).orElseThrow(LatexPreambulaService::predmetNijePronadjen)
                .getLatexPreambula());
    }

    /** Postavlja preambulu ({@code null} ili prazna = podrazumevana); vraća stanje posle izmene. */
    public PreambulaInfo postavi(Long predmetId, PreambulaCmd cmd) {
        String nova = normalizuj(cmd.preambula());
        proveri(nova);
        Predmet predmet = predmetRepository.findByIdForUpdate(predmetId).orElseThrow(LatexPreambulaService::predmetNijePronadjen);
        if (Objects.equals(nova, normalizuj(predmet.getLatexPreambula()))) {
            return info(predmet.getLatexPreambula());
        }
        predmet.setLatexPreambula(nova);
        predmetRepository.save(predmet);
        int varijanti = varijantaRepository.ponistiHashPredmeta(predmetId);
        log.info("Preambula predmeta {} izmenjena ({}), keš rendera poništen za {} varijanti", predmetId,
                nova == null ? "podrazumevana" : nova.length() + " znakova", varijanti);
        return info(nova);
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

    /** Bez razmaka na krajevima, prazno je {@code null}. */
    private static String normalizuj(String preambula) {
        if (preambula == null) return null;
        String s = preambula.strip();
        return s.isEmpty() ? null : s;
    }

    private static PreambulaInfo info(String preambula) {
        String upisana = normalizuj(preambula);
        return new PreambulaInfo(upisana, upisana == null, LatexDokument.PODRAZUMEVANA_PREAMBULA);
    }

    private static SystemException predmetNijePronadjen() {
        return new SystemException(GrupaZadatakaService.PREDMET_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
    }
}
