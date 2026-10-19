package tri.novica.gfssystem.mcp;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.mcp.proba.LosaFasada;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Granica pristupa MCP paketa (spec MCP 2, "Opseg pristupa se proverava testom", fail-closed), nad glavnim klasama
 * ({@code tri.novica.gfssystem}, bez testova; isto što bi uvezao {@code @AnalyzeClasses(packages = "tri.novica.gfssystem",
 * importOptions = DoNotIncludeTests)}, ali kroz običan JUnit test, da pravila sigurno rade i pod JUnit 6):
 * <ol type="a">
 *   <li>alati ({@code mcp.alati}) zavise samo od fasada i ostatka {@code mcp}, DTO-ova, izuzetaka, {@code Promena},
 *       enum-a entiteta i MCP/Spring AI API-ja;</li>
 *   <li>ništa u {@code mcp} ne dodiruje repozitorijume, {@code EntityManager}, JDBC, kontekst/bean factory, ne-enum
 *       entitete ni REST kontrolere;</li>
 *   <li>bez field/setter injection-a;</li>
 *   <li>svaki poziv iz {@code mcp} ka bilo čemu u {@code service..} je na {@link #DOZVOLJENE_METODE} (metode servisnih
 *       bean-ova) ili {@link #DOZVOLJENI_POMOCNICI} (statički čisti pomoćnici); ključ {@code Vlasnik.metoda(Tip, Tip)}
 *       prostih imena, ugnežđen vlasnik {@code Spolja.Unutra};</li>
 *   <li>(d2) svaka stavka spiska postoji i prave je vrste; (e) {@code LosaFasada} krši (d); (f) nijedna zabranjena metoda
 *       iz spec-a nije na spisku.</li>
 * </ol>
 * Nova metoda servisa koju fasada zove ide u {@link #DOZVOLJENE_METODE} svesno, uz pregled.
 */
class McpOpsegTest {

    static final String KOREN = "tri.novica.gfssystem";
    static final String MCP = KOREN + ".mcp";
    static final String SERVIS = KOREN + ".service";

    /** Metode servisnih bean-ova koje fasade smeju da zovu. */
    static final Set<String> DOZVOLJENE_METODE = Set.of(
            // McpKontekst: kontekst, preambula, tekstovi domaćih
            "PredmetService.findAll()",
            "GrupaZadatakaService.stablo(Long, Namena)",
            "PrezentacijaService.lista(Long)",
            "IskljuceneTemeService.lista(Long)",
            "LatexPreambulaService.preambula(Long)",
            "SerijaService.poslednje(int)",
            "DomaciService.tekstoviPredmeta(Long, Pageable)");

    /** Statički čisti pomoćnici iz {@code service..} ({@code public static} u {@code final} klasi koja nije bean). */
    static final Set<String> DOZVOLJENI_POMOCNICI = Set.of(
            "TokenHash.od(String)",
            "PoljaJson.polja(String)",
            "PoljaJson.odgovori(String)");

    /** Servisi van dozvoljenih oblasti (spec MCP 2): studenti, grupe, ocene, testovi i polaganja, predavanja i aktivnosti,
     * onboarding i prijave, beleške i pregledi, izvođenja i njihovi rezultati. Po punom imenu klase, ne po prefiksu. */
    static final Set<String> ZABRANJENE_KLASE = Set.of(
            SERVIS + ".StudentService",
            SERVIS + ".GrupaService",
            SERVIS + ".OcenjivanjeService",
            SERVIS + ".TestService",
            SERVIS + ".PredavanjeService",
            SERVIS + ".OnboardingService",
            SERVIS + ".BeleskaService",
            SERVIS + ".PregledService",
            SERVIS + ".PregledGrupeService",
            SERVIS + ".uzivo.IzvodjenjeService",
            SERVIS + ".uzivo.IzvodjenjeOdrzavanje",
            SERVIS + ".uzivo.StanjeService",
            SERVIS + ".uzivo.OdgovorService",
            SERVIS + ".uzivo.UcesnikService",
            SERVIS + ".uzivo.RezultatBuilder",
            SERVIS + ".uzivo.UzivoObjavljivac");

    /** Pojedinačne zabranjene metode dozvoljenih servisa (puno ime vlasnika + ime metode, svi overload-i). */
    static final Set<String> ZABRANJENE_METODE = Set.of(
            SERVIS + ".PredmetService.getUkupnePoene",
            SERVIS + ".PredmetService.save",
            SERVIS + ".uzivo.PrezentacijaService.obrisi",
            SERVIS + ".uzivo.PrezentacijaService.predavanjaZaPokretanje",
            SERVIS + ".banka.VarijantaService.odobri",
            SERVIS + ".banka.VarijantaService.arhiviraj",
            SERVIS + ".banka.VarijantaService.vratiUNacrt",
            SERVIS + ".banka.VarijantaService.obrisi",
            SERVIS + ".banka.LatexPreambulaService.postavi",
            SERVIS + ".uzivo.MedijService.obrisi",
            SERVIS + ".uzivo.MedijService.sacuvajRender");

    static final JavaClasses KLASE = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(KOREN);

    static boolean uPaketu(JavaClass k, String paket) {
        String p = k.getPackageName();
        return p.equals(paket) || p.startsWith(paket + ".");
    }

    // (a)
    static final DescribedPredicate<JavaClass> DOZVOLJENO_ZA_ALATE = DescribedPredicate.describe(
            "fasade i ostatak mcp (bez mcp.rest), DTO-ovi, izuzeci, Promena, enum-i entiteta, java, Spring AI, MCP SDK, "
                    + "stereotype, lombok, slf4j", klasa -> {
                JavaClass k = klasa.isArray() ? klasa.getBaseComponentType() : klasa;
                return k.isPrimitive()
                        || uPaketu(k, "java") || uPaketu(k, "org.springframework.ai") || uPaketu(k, "io.modelcontextprotocol")
                        || uPaketu(k, "org.springframework.stereotype") || uPaketu(k, "lombok") || uPaketu(k, "org.slf4j")
                        || (uPaketu(k, MCP) && !uPaketu(k, MCP + ".rest"))
                        || uPaketu(k, KOREN + ".dto") || uPaketu(k, KOREN + ".exceptions")
                        || k.getName().equals(KOREN + ".utility.Promena")
                        || (uPaketu(k, KOREN + ".entity") && k.isEnum());
            });

    static final ArchRule ALATI = classes().that().resideInAPackage(MCP + ".alati..")
            .should().onlyDependOnClassesThat(DOZVOLJENO_ZA_ALATE);

    // (b)
    static final DescribedPredicate<JavaClass> ZABRANJENO_ZA_MCP = DescribedPredicate.describe(
            "repozitorijumi, EntityManager, JDBC, ApplicationContext/BeanFactory, ne-enum entiteti, REST kontroleri",
            k -> k.isAssignableTo(Repository.class) || k.isAssignableTo(EntityManager.class)
                    || k.isAssignableTo(EntityManagerFactory.class) || uPaketu(k, "org.springframework.jdbc")
                    || k.isAssignableTo(BeanFactory.class)
                    || (uPaketu(k, KOREN + ".entity") && !k.isEnum())
                    || uPaketu(k, KOREN + ".rest"));

    static final ArchRule BEZ_BAZE = noClasses().that().resideInAPackage(MCP + "..")
            .should().dependOnClassesThat(ZABRANJENO_ZA_MCP);

    // (c)
    static final ArchRule BEZ_INJEKCIJE_POLJA = noFields().that().areDeclaredInClassesThat().resideInAPackage(MCP + "..")
            .should().beAnnotatedWith(Autowired.class)
            .orShould().beAnnotatedWith(Value.class)
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .orShould().beAnnotatedWith("javax.inject.Inject")
            .orShould().beAnnotatedWith(PersistenceContext.class);

    static final ArchRule BEZ_INJEKCIJE_METODA = noMethods().that().areDeclaredInClassesThat().resideInAPackage(MCP + "..")
            .should().beAnnotatedWith(Autowired.class)
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .orShould().beAnnotatedWith("javax.inject.Inject");

    // (d)
    static String ime(JavaClass k) {
        return k.getEnclosingClass().map(s -> ime(s) + ".").orElse("") + k.getSimpleName();
    }

    static String kljuc(JavaClass vlasnik, String metoda, List<JavaClass> parametri) {
        return ime(vlasnik) + "." + metoda
                + parametri.stream().map(JavaClass::getSimpleName).collect(Collectors.joining(", ", "(", ")"));
    }

    static final ArchCondition<JavaClass> SAMO_SA_SPISKA = new ArchCondition<>(
            "zvati iz service.. samo metode sa DOZVOLJENE_METODE ili DOZVOLJENI_POMOCNICI") {
        @Override
        public void check(JavaClass klasa, ConditionEvents dogadjaji) {
            for (JavaAccess<?> pristup : klasa.getAccessesFromSelf()) {
                JavaClass vlasnik = pristup.getTargetOwner();
                if (!uPaketu(vlasnik, SERVIS)) {
                    continue;
                }
                String kljuc = pristup instanceof JavaCodeUnitAccess<?> poziv
                        ? kljuc(vlasnik, poziv.getTarget().getName(), poziv.getTarget().getRawParameterTypes())
                        : ime(vlasnik) + "." + pristup.getName();
                boolean dozvoljeno = pristup instanceof JavaCodeUnitAccess<?>
                        && (DOZVOLJENE_METODE.contains(kljuc) || DOZVOLJENI_POMOCNICI.contains(kljuc));
                if (!dozvoljeno) {
                    dogadjaji.add(SimpleConditionEvent.violated(pristup,
                            kljuc + " nije na spisku (" + pristup.getDescription() + ")"));
                }
            }
        }
    };

    static final ArchRule POZIVI_SERVISA = classes().that().resideInAPackage(MCP + "..").should(SAMO_SA_SPISKA);

    @Test
    void alatiZaviseSamoOdDozvoljenog() {
        ALATI.check(KLASE);
    }

    @Test
    void mcpNeDodirujeBazuKontekstNiRest() {
        BEZ_BAZE.check(KLASE);
    }

    @Test
    void bezInjekcijePoljaIMetoda() {
        BEZ_INJEKCIJE_POLJA.check(KLASE);
        BEZ_INJEKCIJE_METODA.check(KLASE);
    }

    @Test
    void poziviServisaSamoSaSpiska() {
        POZIVI_SERVISA.check(KLASE);
    }

    /** Indeks metoda klasa iz {@code service..}: ključ -> metoda. */
    static Map<String, JavaMethod> metodeServisa() {
        Map<String, JavaMethod> indeks = new HashMap<>();
        for (JavaClass k : KLASE) {
            if (uPaketu(k, SERVIS)) {
                for (JavaMethod m : k.getMethods()) {
                    indeks.put(kljuc(k, m.getName(), m.getRawParameterTypes()), m);
                }
            }
        }
        return indeks;
    }

    static boolean bean(JavaClass k) {
        return k.isAnnotatedWith(Component.class) || k.isMetaAnnotatedWith(Component.class);
    }

    @Test
    void stavkeSpiskaPostojeIPraveSuVrste() {
        Map<String, JavaMethod> indeks = metodeServisa();
        List<String> greske = new ArrayList<>();
        for (String stavka : DOZVOLJENE_METODE) {
            JavaMethod m = indeks.get(stavka);
            if (m == null || !m.getModifiers().contains(JavaModifier.PUBLIC) || !bean(m.getOwner())) {
                greske.add(stavka + " nije javna metoda klase sa @Service/@Component");
            }
        }
        for (String stavka : DOZVOLJENI_POMOCNICI) {
            JavaMethod m = indeks.get(stavka);
            if (m == null || !m.getModifiers().containsAll(Set.of(JavaModifier.PUBLIC, JavaModifier.STATIC))
                    || !m.getOwner().getModifiers().contains(JavaModifier.FINAL) || bean(m.getOwner())) {
                greske.add(stavka + " nije public static metoda final klase koja nije bean");
            }
        }
        assertEquals(List.of(), greske);
    }

    @Test
    void losaFasadaKrsiPravilo() {
        JavaClasses losa = new ClassFileImporter().importClasses(LosaFasada.class);
        EvaluationResult rezultat = POZIVI_SERVISA.evaluate(losa);
        assertTrue(rezultat.hasViolation());
        String izvestaj = String.join("\n", rezultat.getFailureReport().getDetails());
        assertTrue(izvestaj.contains("VarijantaService.odobri(Long) nije na spisku"), izvestaj);
    }

    /** Stavke spiska čiji je vlasnik zabranjena klasa ili koje su zabranjena metoda. */
    static List<String> zabranjene(Set<String> metode) {
        Map<String, JavaMethod> indeks = metodeServisa();
        List<String> zabranjene = new ArrayList<>();
        for (String stavka : metode) {
            JavaMethod m = indeks.get(stavka);
            if (m == null) {
                zabranjene.add(stavka + " (ne postoji)");
                continue;
            }
            String vlasnik = m.getOwner().getName();
            if (ZABRANJENE_KLASE.contains(vlasnik) || ZABRANJENE_METODE.contains(vlasnik + "." + m.getName())) {
                zabranjene.add(stavka);
            }
        }
        return zabranjene;
    }

    @Test
    void zabranjeneMetodeNisuNaSpisku() {
        assertEquals(List.of(), zabranjene(DOZVOLJENE_METODE));
    }

    @Test
    void zabranaPrepoznajeKlasuIMetoduNeIPrefiks() {
        Set<String> spisak = Set.of("VarijantaService.odobri(Long)", "PredmetService.save(CreatePredmetCmd)",
                "StudentService.findAll()", "PredmetService.findAll()");
        List<String> nadjene = zabranjene(spisak);
        assertTrue(nadjene.containsAll(List.of("VarijantaService.odobri(Long)", "PredmetService.save(CreatePredmetCmd)",
                "StudentService.findAll()")), nadjene.toString());
        assertFalse(nadjene.contains("PredmetService.findAll()"), nadjene.toString());
    }

    @Test
    void zabranjeneKlaseIMetodePostoje() {
        Set<String> klase = KLASE.stream().map(JavaClass::getName).collect(Collectors.toSet());
        for (String k : ZABRANJENE_KLASE) {
            assertTrue(klase.contains(k), "nema klase " + k);
        }
        Set<String> metode = metodeServisa().values().stream()
                .map(m -> m.getOwner().getName() + "." + m.getName()).collect(Collectors.toSet());
        for (String m : ZABRANJENE_METODE) {
            assertTrue(metode.contains(m), "nema metode " + m);
        }
    }
}
