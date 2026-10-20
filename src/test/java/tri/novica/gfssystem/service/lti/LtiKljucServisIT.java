package tri.novica.gfssystem.service.lti;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tri.novica.gfssystem.repository.lti.LtiKljucRepository;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ključ alata (spec 8.2) nad pravom MySQL bazom: pravi se jednom, trajan u bazi, JWKS bez privatnih delova. */
@SpringBootTest
class LtiKljucServisIT {

    static final Set<String> PRIVATNI_DELOVI = Set.of("d", "p", "q", "dp", "dq", "qi", "oth", "k");

    @Autowired LtiKljucServis servis;
    @Autowired LtiKljucRepository repo;
    @Autowired TokenGenerator tokeni;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager tm;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void ocisti() {
        jdbc.update("delete from lti_kljucevi");
    }

    @Test
    void prviPozivPraviRsa2048SaKidOd16Znakova() throws Exception {
        RSAKey k = servis.aktivni();
        assertTrue(k.isPrivate());
        assertEquals(2048, k.toRSAPublicKey().getModulus().bitLength());
        assertEquals(16, k.getKeyID().length());
        assertTrue(k.getKeyID().chars().allMatch(c -> TokenGenerator.ALFABET.indexOf(c) >= 0));
        assertEquals(1, repo.count());
    }

    @Test
    void drugiPozivVracaIstiKljucBezNovogReda() {
        RSAKey a = servis.aktivni();
        RSAKey b = servis.aktivni();
        assertEquals(a.getKeyID(), b.getKeyID());
        assertEquals(1, repo.count());
    }

    @Test
    void istiKidPosleRestartaKonteksta() throws Exception {
        RSAKey pre = servis.aktivni();
        // novi servis nad istom bazom = novi kontekst: ništa se ne sme čuvati samo u memoriji
        LtiKljucServis novi = new LtiKljucServis(repo, tokeni, clock, tm);
        RSAKey posle = novi.aktivni();
        assertEquals(pre.getKeyID(), posle.getKeyID());
        assertEquals(pre.toRSAPrivateKey().getPrivateExponent(), posle.toRSAPrivateKey().getPrivateExponent());
        assertEquals(1, repo.count());
    }

    @Test
    void javniImaJedanKljucBezPrivatnihDelova() throws Exception {
        RSAKey aktivni = servis.aktivni();
        JWKSet skup = servis.javni();
        assertEquals(1, skup.getKeys().size());
        JWK k = skup.getKeys().get(0);
        assertFalse(k.isPrivate());
        assertEquals(aktivni.getKeyID(), k.getKeyID());
        assertEquals("sig", k.getKeyUse().identifier());
        assertEquals("RS256", k.getAlgorithm().getName());
        Map<String, Object> json = skup.toJSONObject();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kljucevi = (List<Map<String, Object>>) json.get("keys");
        assertEquals(1, kljucevi.size());
        for (String deo : PRIVATNI_DELOVI) {
            assertFalse(kljucevi.get(0).containsKey(deo), "privatni deo u JWKS-u: " + deo);
        }
    }

    @Test
    void javniPraviKljucAkoGaNema() {
        assertEquals(0, repo.count());
        assertEquals(1, servis.javni().getKeys().size());
        assertEquals(1, repo.count());
    }

    @Test
    void neaktivanKljucNijeUJwksu() {
        RSAKey stari = servis.aktivni();
        jdbc.update("update lti_kljucevi set aktivan = 0 where kid = ?", stari.getKeyID());
        RSAKey novi = servis.aktivni();
        assertNotEquals(stari.getKeyID(), novi.getKeyID());
        assertEquals(List.of(novi.getKeyID()), servis.javni().getKeys().stream().map(JWK::getKeyID).toList());
    }

    @Test
    void privatniKljucJeUBaziKaoPkcs8() {
        servis.aktivni();
        String privatni = jdbc.queryForObject("select privatni from lti_kljucevi", String.class);
        assertNotNull(privatni);
        assertFalse(privatni.isBlank());
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(privatni));
    }
}
