package tri.novica.gfssystem.validation;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.dto.uzivo.OpcijaCmd;
import tri.novica.gfssystem.dto.uzivo.PitanjeCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.TekstPrikaz;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pravila za {@link SlajdCmd} (spec 4.1 "Validacija SlajdCmd", granice 2.12). Ne dira bazu: postojanje slike proverava
 * servis. Redosled upotrebe: {@code proveri(normalizuj(cmd))}. Dužine se broje u znakovima kao u MySQL koloni (code
 * point), pa nijedna prihvaćena vrednost ne prelazi kolonu.
 */
public final class SlajdPP {

    public static final int MAX_SLAJDOVA = 200;
    public static final String TACNO = "Tačno";
    public static final String NETACNO = "Netačno";

    private static final int MAX_NASLOV = 300;
    private static final int MAX_SADRZAJ = 20_000;
    private static final int MAX_BELESKE = 5_000;
    private static final int MAX_TEKST_PITANJA = 2_000;
    private static final int MIN_OPCIJA = 2;
    private static final int MAX_OPCIJA = 6;
    private static final int MAX_TEKST_OPCIJE = 300;
    private static final int MIN_VREME = 5;
    private static final int MAX_VREME = 600;
    private static final int MAX_JEDINICA = 30;
    private static final int MAX_PRIHVATLJIVIH = 20;
    private static final int MAX_PRIHVATLJIV = 100;
    private static final int MAX_OZNAKA_SKALE = 60;

    private SlajdPP() {}

    /**
     * Kopija sa podrazumevanim vrednostima: tekstovi bez razmaka na krajevima, prazan string {@code null}; INFO bez
     * pitanja; polja koja ne važe za tip pitanja su {@code null} (opcije prazna lista); ANKETA bez tačnih,
     * TACNO_NETACNO sa tekstovima "Tačno"/"Netačno", KRATAK_TEKST podrazumevano OBLAK, BROJ odstupanje 0 APSOLUTNO.
     */
    public static SlajdCmd normalizuj(SlajdCmd cmd) {
        if (cmd == null) return null;
        PitanjeCmd pitanje = cmd.tip() == TipSlajda.INFO ? null : normalizuj(cmd.pitanje());
        return new SlajdCmd(cmd.tip(), prazanUNull(cmd.naslov()), prazanUNull(cmd.sadrzaj()), prazanUNull(cmd.slikaId()),
                prazanUNull(cmd.beleske()), cmd.postepeno(), pitanje);
    }

    private static PitanjeCmd normalizuj(PitanjeCmd p) {
        if (p == null) return null;
        TipPitanja tip = p.tip();
        List<OpcijaCmd> opcije = new ArrayList<>();
        if (imaOpcije(tip) && p.opcije() != null) {
            for (int i = 0; i < p.opcije().size(); i++) {
                OpcijaCmd o = p.opcije().get(i);
                String tekst = o == null ? null : prazanUNull(o.tekst());
                boolean tacna = o != null && o.tacna() && tip != TipPitanja.ANKETA;
                if (tip == TipPitanja.TACNO_NETACNO && i < 2) {
                    tekst = i == 0 ? TACNO : NETACNO;
                }
                opcije.add(new OpcijaCmd(tekst, tacna));
            }
        }
        boolean broj = tip == TipPitanja.BROJ;
        boolean kratakTekst = tip == TipPitanja.KRATAK_TEKST;
        boolean skala = tip == TipPitanja.SKALA;
        return new PitanjeCmd(tip, prazanUNull(p.tekst()), prazanUNull(p.slikaId()), p.vremeSekunde(), opcije,
                broj ? p.brojTacno() : null,
                broj ? Objects.requireNonNullElse(p.brojOdstupanje(), 0.0) : null,
                broj ? Objects.requireNonNullElse(p.odstupanjeTip(), OdstupanjeTip.APSOLUTNO) : null,
                broj ? prazanUNull(p.jedinica()) : null,
                kratakTekst ? Objects.requireNonNullElse(p.tekstPrikaz(), TekstPrikaz.OBLAK) : null,
                kratakTekst ? prihvatljivi(p.prihvatljiviOdgovori()) : null,
                skala ? prazanUNull(p.skalaMinOznaka()) : null,
                skala ? prazanUNull(p.skalaMaxOznaka()) : null);
    }

    /** Baca 400 sa porukom prvog prekršenog pravila. Prazan tekst (samo razmaci) se računa kao da ga nema. */
    public static void proveri(SlajdCmd cmd) {
        if (cmd == null || cmd.tip() == null) {
            throw greska("Tip slajda je obavezan.");
        }
        if (duzina(cmd.naslov()) > MAX_NASLOV) {
            throw greska("Naslov može imati najviše 300 znakova.");
        }
        if (duzina(cmd.sadrzaj()) > MAX_SADRZAJ) {
            throw greska("Tekst slajda može imati najviše 20000 znakova.");
        }
        if (duzina(cmd.beleske()) > MAX_BELESKE) {
            throw greska("Beleške mogu imati najviše 5000 znakova.");
        }
        if (cmd.tip() == TipSlajda.INFO) {
            if (prazan(cmd.naslov()) && prazan(cmd.sadrzaj()) && prazan(cmd.slikaId())) {
                throw greska("Info slajd mora imati naslov, tekst ili sliku.");
            }
            return;
        }
        if (cmd.pitanje() == null) {
            throw greska("Slajd sa pitanjem mora imati pitanje.");
        }
        proveri(cmd.pitanje());
    }

    private static void proveri(PitanjeCmd p) {
        if (p.tip() == null) {
            throw greska("Tip pitanja je obavezan.");
        }
        if (prazan(p.tekst()) || duzina(p.tekst()) > MAX_TEKST_PITANJA) {
            throw greska("Tekst pitanja je obavezan (najviše 2000 znakova).");
        }
        if (p.vremeSekunde() != null && (p.vremeSekunde() < MIN_VREME || p.vremeSekunde() > MAX_VREME)) {
            throw greska("Vreme za odgovor mora biti od 5 do 600 sekundi.");
        }
        switch (p.tip()) {
            case JEDAN_TACAN, VISE_TACNIH, ANKETA -> proveriOpcije(p);
            case TACNO_NETACNO -> {
                if (p.opcije() == null || p.opcije().size() != 2 || brojTacnih(p.opcije()) != 1) {
                    throw greska("Tačno/netačno pitanje ima tačno dva odgovora, od kojih je jedan tačan.");
                }
            }
            case KRATAK_TEKST -> {
                List<String> prihvatljivi = p.prihvatljiviOdgovori();
                if (prihvatljivi != null && (prihvatljivi.size() > MAX_PRIHVATLJIVIH
                        || prihvatljivi.stream().anyMatch(s -> duzina(s) > MAX_PRIHVATLJIV))) {
                    throw greska("Najviše 20 prihvatljivih odgovora, svaki do 100 znakova.");
                }
            }
            case BROJ -> {
                if (nijeKonacan(p.brojTacno()) || nijeKonacan(p.brojOdstupanje())) {
                    throw greska("Broj nije ispravan.");
                }
                if (p.brojOdstupanje() != null && p.brojOdstupanje() < 0) {
                    throw greska("Odstupanje ne može biti negativno.");
                }
                if (duzina(p.jedinica()) > MAX_JEDINICA) {
                    throw greska("Jedinica može imati najviše 30 znakova.");
                }
            }
            case SKALA -> {
                if (duzina(p.skalaMinOznaka()) > MAX_OZNAKA_SKALE || duzina(p.skalaMaxOznaka()) > MAX_OZNAKA_SKALE) {
                    throw greska("Oznaka skale može imati najviše 60 znakova.");
                }
            }
        }
    }

    private static void proveriOpcije(PitanjeCmd p) {
        List<OpcijaCmd> opcije = p.opcije();
        if (opcije == null || opcije.size() < MIN_OPCIJA || opcije.size() > MAX_OPCIJA) {
            throw greska("Pitanje mora imati od 2 do 6 ponuđenih odgovora.");
        }
        for (OpcijaCmd o : opcije) {
            if (o == null || prazan(o.tekst()) || duzina(o.tekst()) > MAX_TEKST_OPCIJE) {
                throw greska("Ponuđeni odgovor mora imati od 1 do 300 znakova.");
            }
        }
        long tacnih = brojTacnih(opcije);
        if (p.tip() == TipPitanja.JEDAN_TACAN && tacnih != 1) {
            throw greska("Pitanje sa jednim tačnim odgovorom mora imati tačno jedan tačan odgovor.");
        }
        if (p.tip() == TipPitanja.VISE_TACNIH && tacnih < 1) {
            throw greska("Označi bar jedan tačan odgovor.");
        }
    }

    /** Pre dodavanja slajda: {@code postojeci} je trenutni broj slajdova prezentacije. */
    public static void proveriBrojSlajdova(long postojeci) {
        if (postojeci >= MAX_SLAJDOVA) {
            throw greska("Prezentacija može imati najviše 200 slajdova.");
        }
    }

    /** Tipovi pitanja sa ponuđenim odgovorima. */
    public static boolean imaOpcije(TipPitanja tip) {
        return tip == TipPitanja.JEDAN_TACAN || tip == TipPitanja.VISE_TACNIH || tip == TipPitanja.ANKETA
                || tip == TipPitanja.TACNO_NETACNO;
    }

    private static long brojTacnih(List<OpcijaCmd> opcije) {
        return opcije.stream().filter(o -> o != null && o.tacna()).count();
    }

    private static List<String> prihvatljivi(List<String> lista) {
        if (lista == null) return new ArrayList<>();
        List<String> rezultat = new ArrayList<>();
        for (String s : lista) {
            String t = prazanUNull(s);
            if (t != null) rezultat.add(t);
        }
        return rezultat;
    }

    private static boolean nijeKonacan(Double d) {
        return d != null && !Double.isFinite(d);
    }

    private static boolean prazan(String s) {
        return s == null || s.isBlank();
    }

    private static int duzina(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    private static String prazanUNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static SystemException greska(String poruka) {
        return new SystemException(poruka, HttpStatus.BAD_REQUEST);
    }
}
