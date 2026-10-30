package tri.novica.gfssystem.service.push;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;

/**
 * Kriva P-256 (secp256r1) samo iz JDK-a: nekompresovana tačka ({@code 0x04 || X || Y}, 65 bajtova) kako je šalje browser
 * ({@code p256dh}) i kako ide u VAPID ključ, i privatni skalar od 32 bajta. Tačka se pre upotrebe proverava (na krivoj, u
 * polju), da tuđa tačka ne uđe u ECDH.
 */
public final class P256 {

    public static final int DUZINA_TACKE = 65;
    public static final int DUZINA_SKALARA = 32;
    static final ECParameterSpec PARAMETRI;
    private static final BigInteger P;
    private static final BigInteger A;
    private static final BigInteger B;

    static {
        try {
            AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
            ap.init(new ECGenParameterSpec("secp256r1"));
            PARAMETRI = ap.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 nije dostupna", e);
        }
        EllipticCurve k = PARAMETRI.getCurve();
        P = ((ECFieldFp) k.getField()).getP();
        A = k.getA();
        B = k.getB();
    }

    private P256() {}

    /** Javni ključ iz nekompresovane tačke; {@link IllegalArgumentException} kad tačka nije ispravna. */
    public static ECPublicKey javni(byte[] tacka) {
        if (tacka == null || tacka.length != DUZINA_TACKE || tacka[0] != 0x04) {
            throw new IllegalArgumentException("Tačka nije nekompresovana P-256");
        }
        BigInteger x = new BigInteger(1, java.util.Arrays.copyOfRange(tacka, 1, 33));
        BigInteger y = new BigInteger(1, java.util.Arrays.copyOfRange(tacka, 33, 65));
        if (!naKrivoj(x, y)) {
            throw new IllegalArgumentException("Tačka nije na krivoj P-256");
        }
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), PARAMETRI));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Javni ključ nije ispravan", e);
        }
    }

    /** Da li je {@code bajtovi} ispravna nekompresovana P-256 tačka (bez izuzetka). */
    public static boolean ispravnaTacka(byte[] bajtovi) {
        try {
            javni(bajtovi);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static byte[] tacka(ECPublicKey k) {
        byte[] t = new byte[DUZINA_TACKE];
        t[0] = 0x04;
        upisi(k.getW().getAffineX(), t, 1);
        upisi(k.getW().getAffineY(), t, 33);
        return t;
    }

    public static ECPrivateKey privatni(byte[] skalar) {
        if (skalar == null || skalar.length != DUZINA_SKALARA) {
            throw new IllegalArgumentException("Skalar nije 32 bajta");
        }
        BigInteger s = new BigInteger(1, skalar);
        if (s.signum() == 0 || s.compareTo(PARAMETRI.getOrder()) >= 0) {
            throw new IllegalArgumentException("Skalar nije u opsegu");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(s, PARAMETRI));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Privatni ključ nije ispravan", e);
        }
    }

    public static byte[] skalar(ECPrivateKey k) {
        byte[] b = new byte[DUZINA_SKALARA];
        upisi(k.getS(), b, 0);
        return b;
    }

    /** Nov par ključeva (efemerni za šifrovanje poruke ili VAPID par). */
    public static KeyPair nov(SecureRandom random) {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), random);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 par nije napravljen", e);
        }
    }

    static boolean naKrivoj(BigInteger x, BigInteger y) {
        if (x.signum() < 0 || y.signum() < 0 || x.compareTo(P) >= 0 || y.compareTo(P) >= 0) {
            return false;
        }
        BigInteger levo = y.modPow(BigInteger.TWO, P);
        BigInteger desno = x.modPow(BigInteger.valueOf(3), P).add(A.multiply(x)).add(B).mod(P);
        return levo.equals(desno);
    }

    /** Neoznačen broj u tačno 32 bajta (sa nulama spreda). */
    private static void upisi(BigInteger n, byte[] cilj, int od) {
        byte[] b = n.toByteArray();
        int pocetak = b.length > DUZINA_SKALARA ? b.length - DUZINA_SKALARA : 0;
        int duzina = b.length - pocetak;
        System.arraycopy(b, pocetak, cilj, od + DUZINA_SKALARA - duzina, duzina);
    }
}
