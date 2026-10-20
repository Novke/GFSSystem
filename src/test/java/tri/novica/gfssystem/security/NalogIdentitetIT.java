package tri.novica.gfssystem.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.service.prijava.TestStudenti;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Šav za fazu 2 banke (spec 13.1): {@link IdentitetStudenta} čita {@code gfs_student}; jedini {@code @Primary} je nalog. */
@SpringBootTest
@AutoConfigureMockMvc
class NalogIdentitetIT {

    @Autowired IdentitetStudenta identitet;
    @Autowired StudentskaPrijava prijava;
    @Autowired StudentRepository studentRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConfigurableListableBeanFactory beanFactory;

    TestStudenti studenti;

    @BeforeEach
    void setUp() {
        studenti = new TestStudenti(studentRepository, jdbc);
    }

    @AfterEach
    void ocisti() {
        SecurityContextHolder.clearContext();
        studenti.obrisi();
    }

    static void kao(org.springframework.security.core.Authentication a) {
        SecurityContextHolder.getContext().setAuthentication(a);
    }

    String token(Student s) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        prijava.prijavi(s.getId(), NacinPrijave.KOD, new MockHttpServletRequest(), res);
        String h = res.getHeader("Set-Cookie");
        return h.substring("gfs_student=".length(), h.indexOf(';'));
    }

    static MockHttpServletRequest sa(Cookie... kolacici) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (kolacici.length > 0) req.setCookies(kolacici);
        return req;
    }

    @Test
    void bezKolacicaJePrazno() {
        assertEquals(Optional.empty(), identitet.studentId(sa()));
        assertEquals(Optional.empty(), identitet.studentId(sa(new Cookie(Kolacici.NASTAVNIK, new TokenGenerator().novi()))));
    }

    @Test
    void vazeciKolacicDajeStudenta() {
        Student s = studenti.napravi("pera@example.test");
        assertEquals(Optional.of(s.getId()), identitet.studentId(sa(new Cookie(Kolacici.STUDENT, token(s)))));
    }

    @Test
    void opozvanJePrazno() {
        Student s = studenti.napravi("pera@example.test");
        String t = token(s);
        prijava.odjavi(t);
        assertEquals(Optional.empty(), identitet.studentId(sa(new Cookie(Kolacici.STUDENT, t))));
        assertEquals(Optional.empty(), identitet.studentId(sa(new Cookie(Kolacici.STUDENT, "nije-token"))));
    }

    @Test
    void podmetnutKolacicIstogImenaNeSmeta() {
        Student s = studenti.napravi("pera@example.test");
        Cookie podmetnut = new Cookie(Kolacici.STUDENT, new TokenGenerator().novi());
        Cookie pravi = new Cookie(Kolacici.STUDENT, token(s));
        assertEquals(Optional.of(s.getId()), identitet.studentId(sa(podmetnut, pravi)));
    }

    /** Review B7 #5: u zahtevu kroz lanac identitet je ono što je filter već utvrdio (bez novog upita i produženja). */
    @Test
    void studentIzLancaBezPonovneProvere() {
        kao(new StudentAutentikacija(42L, 7L));
        assertEquals(Optional.of(42L), identitet.studentId(sa()));
    }

    /** Nastavnik ima prednost (spec 2.2): i uz važeći studentski kolačić zahtev nije studentski. */
    @Test
    void nastavnikImaPrednost() {
        Student s = studenti.napravi("pera@example.test");
        kao(new NastavnikAutentikacija(1L));
        assertEquals(Optional.empty(), identitet.studentId(sa(new Cookie(Kolacici.STUDENT, token(s)))));
    }

    /** Lanac je prošao, a filter nije prepoznao kolačić (anoniman zahtev): ne proverava se ponovo. */
    @Test
    void anonimanIzLancaJePrazan() {
        Student s = studenti.napravi("pera@example.test");
        kao(new AnonymousAuthenticationToken("k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals(Optional.empty(), identitet.studentId(sa(new Cookie(Kolacici.STUDENT, token(s)))));
    }

    @Test
    void dostupno() {
        assertTrue(identitet.dostupno());
        assertInstanceOf(NalogIdentitet.class, identitet);
    }

    @Test
    void tacnoJedanPrimaryIdentitetStudenta() {
        String[] imena = beanFactory.getBeanNamesForType(IdentitetStudenta.class);
        long primarnih = Arrays.stream(imena).filter(i -> beanFactory.getBeanDefinition(i).isPrimary()).count();
        assertEquals(1, primarnih, "IdentitetStudenta beanovi: " + Arrays.toString(imena));
        // NemaIdentiteta se ne pravi kad nalog postoji (ConditionalOnMissingBean)
        assertEquals(1, imena.length, Arrays.toString(imena));
    }
}
