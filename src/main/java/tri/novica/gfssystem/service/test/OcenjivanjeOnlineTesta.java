package tri.novica.gfssystem.service.test;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.test.online.OcenaPoljaNastavnik;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.Test;
import tri.novica.gfssystem.entity.test.TestIspravka;
import tri.novica.gfssystem.entity.test.TestPrimerak;
import tri.novica.gfssystem.entity.test.TestPrimerakZadatak;
import tri.novica.gfssystem.repository.PolaganjeRepository;
import tri.novica.gfssystem.repository.test.TestIspravkaRepository;
import tri.novica.gfssystem.repository.test.TestPrimerakZadatakRepository;

import java.util.*;

/**
 * Ocena primerka online testa (spec 9.1, 14.1): iz snimka zadataka, unosa i važećih ispravki ključa testa; ručni poeni
 * zadatka pobeđuju automatske. Zbir ide u {@link Polaganje} (izvor istine za poene), red nastaje kad se primerak oceni.
 * Pozivalac drži ključ reda primerka (i, za preračun više primeraka, ključ testa).
 */
@Component
@RequiredArgsConstructor
public class OcenjivanjeOnlineTesta {

    private final TestPrimerakZadatakRepository zadatakRepository;
    private final TestIspravkaRepository ispravkaRepository;
    private final PolaganjeRepository polaganjeRepository;

    /** Važeće ispravke testa po varijanti; poslednja KLJUC ispravka polja pobeđuje. */
    public Map<Long, BodovanjeZadatka.Ispravke> ispravke(Long testId) {
        return ispravke(ispravkaRepository.findByTestIdAndVracenaIsNullOrderByIdAsc(testId));
    }

    static Map<Long, BodovanjeZadatka.Ispravke> ispravke(List<TestIspravka> vazece) {
        Map<Long, Set<String>> ponistena = new HashMap<>();
        Map<Long, Map<String, OdgovorPolja>> kljucevi = new HashMap<>();
        Map<Long, Map<String, Set<String>>> prihvaceni = new HashMap<>();
        for (TestIspravka i : vazece) {
            Long v = i.getVarijantaId();
            switch (i.getVrsta()) {
                case PONISTI -> ponistena.computeIfAbsent(v, x -> new HashSet<>()).add(i.getPolje());
                case KLJUC -> {
                    OdgovorPolja o = TestJson.odgovorPoljaIliNull(i.getVrednost());
                    if (o != null) kljucevi.computeIfAbsent(v, x -> new HashMap<>()).put(i.getPolje(), o);
                }
                case PRIHVATI -> {
                    if (i.getVrednost() != null) {
                        prihvaceni.computeIfAbsent(v, x -> new HashMap<>())
                                .computeIfAbsent(i.getPolje(), x -> new HashSet<>()).add(i.getVrednost());
                    }
                }
            }
        }
        Set<Long> varijante = new HashSet<>();
        varijante.addAll(ponistena.keySet());
        varijante.addAll(kljucevi.keySet());
        varijante.addAll(prihvaceni.keySet());
        Map<Long, BodovanjeZadatka.Ispravke> rezultat = new HashMap<>();
        for (Long v : varijante) {
            rezultat.put(v, new BodovanjeZadatka.Ispravke(ponistena.get(v), kljucevi.get(v), prihvaceni.get(v)));
        }
        return rezultat;
    }

    /** Ocena jednog zadatka (upisuje {@code poeni} i {@code ocena}); ne dira ručne poene. */
    public static void oceniZadatak(TestPrimerakZadatak z, BodovanjeZadatka.Ispravke isp) {
        SnimakZadatkaTesta s = TestJson.snimak(z.getSnimak());
        if (s.rucno()) {
            z.setPoeni(null);
            z.setOcena(null);
            return;
        }
        BodovanjeZadatka.Ishod ishod = BodovanjeZadatka.oceni(s.polja(), s.odgovori(), TestJson.uneto(z.getOdgovori()),
                s.bodovanje(), s.konacnoPriznajeKorake(), z.getMaxPoena(), isp);
        Map<String, OcenaPoljaNastavnik> ocena = new LinkedHashMap<>();
        ishod.polja().forEach((k, u) -> ocena.put(k, new OcenaPoljaNastavnik(u.udeo(), u.izvor().name())));
        z.setPoeni(ishod.poeni());
        z.setOcena(TestJson.uJson(ocena));
    }

    /** Poeni zadatka koji ulaze u zbir: ručni pa automatski; ručni zadatak bez ručnih poena 0. */
    public static double poeniZadatka(TestPrimerakZadatak z) {
        if (z.getRucniPoeni() != null) return z.getRucniPoeni();
        return z.getPoeni() == null ? 0 : z.getPoeni();
    }

    /** Oceni sve zadatke primerka i upiši polaganje. */
    public void oceni(Test test, TestPrimerak p, Map<Long, BodovanjeZadatka.Ispravke> ispravke) {
        List<TestPrimerakZadatak> zadaci = zadatakRepository.zaPrimerakForUpdate(p.getId());
        for (TestPrimerakZadatak z : zadaci) {
            oceniZadatak(z, ispravke.getOrDefault(z.getVarijantaId(), BodovanjeZadatka.Ispravke.NEMA));
        }
        zadatakRepository.saveAll(zadaci);
        upisiZbir(test, p, zadaci);
    }

    /** Zbir zadataka u primerak i polaganje (posle ručnih poena, bez ponovne automatske ocene). */
    public void upisiZbir(Test test, TestPrimerak p, List<TestPrimerakZadatak> zadaci) {
        double zbir = zadaci.stream().mapToDouble(OcenjivanjeOnlineTesta::poeniZadatka).sum();
        p.setPoeni(BodovanjeZadatka.zaokruzi(zbir));
        upisiPolaganje(test, p);
    }

    /** Polaganje primerka: novo ili osveženo (grupa testa, poeni, prepisivao). */
    public void upisiPolaganje(Test test, TestPrimerak p) {
        Polaganje pol = polaganjeRepository.findByTestIdAndStudentId(test.getId(), p.getStudent().getId())
                .orElseGet(() -> Polaganje.defaultPolaganje(test, p.getStudent()));
        pol.setGrupa(p.getGrupaTesta());
        pol.setOstvareniPoeni(p.getPoeni());
        pol.setPrepisivao(p.isPrepisivao());
        polaganjeRepository.save(pol);
    }

    /** Briše polaganje primerka (poništen ili vraćen u rad). */
    public void obrisiPolaganje(Test test, TestPrimerak p) {
        polaganjeRepository.findByTestIdAndStudentId(test.getId(), p.getStudent().getId()).ifPresent(pol -> {
            polaganjeRepository.delete(pol);
            polaganjeRepository.flush();
        });
    }

    /** Nevraćene ispravke tipa za varijantu i polje (za prikaz {@code ponisteno} i važećeg ključa). */
    public static boolean ponisteno(BodovanjeZadatka.Ispravke isp, String polje) {
        return isp != null && isp.ponistena().contains(polje);
    }
}
