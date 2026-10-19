package tri.novica.gfssystem.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tri.novica.gfssystem.dto.uzivo.OpcijaCmd;
import tri.novica.gfssystem.dto.uzivo.PitanjeCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Markdown slike slajda (spec 6.5): cilj sme biti samo {@code api/public/mediji/<uuid>}; spoljni URL, apsolutna putanja,
 * izlazak iz putanje i referentna slika sa spoljnom definicijom su 400. Postojanje i vrstu medija proverava servis.
 */
class SlajdPPMarkdownTest {

    static final String A = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";
    static final String B = "7a9d0c11-2b34-4f56-8a78-9b0c1d2e3f40";
    static final String GFS_A = "api/public/mediji/" + A;
    static final String GFS_B = "api/public/mediji/" + B;

    static void odbijeno(String md) {
        SystemException e = assertThrows(SystemException.class, () -> SlajdPP.slikeIzMarkdowna(md), md);
        assertEquals(400, e.getCode());
        assertEquals(SlajdPP.SLIKA_VAN_GFS, e.getMessage());
    }

    @Test
    void slikaIzGfsaDajeUuid() {
        assertEquals(Set.of(A), SlajdPP.slikeIzMarkdowna("Pogledaj:\n\n![a](" + GFS_A + ")"));
    }

    @Test
    void naslovIUglasteZagradeIDveSlike() {
        assertEquals(Set.of(A, B), SlajdPP.slikeIzMarkdowna(
                "![a](" + GFS_A + " \"naslov\") i ![b](<" + GFS_B + ">)\n![opet](" + GFS_A + " 'n')"));
    }

    @Test
    void bezSlikeIObicanLinkProlaze() {
        assertEquals(Set.of(), SlajdPP.slikeIzMarkdowna(null));
        assertEquals(Set.of(), SlajdPP.slikeIzMarkdowna(""));
        assertEquals(Set.of(), SlajdPP.slikeIzMarkdowna("Tekst sa linkom [a](https://x) i **boldom**!"));
        assertEquals(Set.of(), SlajdPP.slikeIzMarkdowna("Uzvik! [a](https://x)\n\n[r]: https://x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "![a](https://x.invalid/a.png)",
            "![a](/api/public/mediji/" + A + ")",
            "![a](api/public/mediji/../banka)",
            "![a](api/public/mediji/" + A + "/../../banka)",
            "![a](api/public/mediji/3F1C2B9A-6D7E-4C1A-9B2F-0A1B2C3D4E5F)",
            "![a](//x.invalid/a.png)",
            "![a](data:image/png;base64,AAAA)",
            "![a](<https://x.invalid/a.png>)",
            "![a]( https://x.invalid/a.png \"n\")",
            "![a](https://x.invalid/a(1).png)",
            "![a](api/public/mediji/" + A + "?x=1)",
            "![a](api/public/mediji/" + A + "%2F..)",
            "![a](api\\/public/mediji/" + A + ")",
            "![a [b]](https://x.invalid/a.png)",
            "[![a](https://x.invalid/a.png)](" + GFS_A + ")",
            "![a](https://x.invalid/a.png",
            // kod u natpisu krije ] (marked ga ipak čita kao sliku), cilj posle > u <...>
            "![`]`](https://x.invalid/a.png)",
            "![a](<" + GFS_A + ">evil>)",
            "![a]()"})
    void slikaVanGfsaJe400(String md) {
        odbijeno(md);
    }

    @Test
    void ispravnaPaSpoljnaSlikaJe400() {
        odbijeno("![a](" + GFS_A + ")\n\n![b](https://x.invalid/b.png)");
    }

    @Test
    void referentnaSlikaSaSpoljnomDefinicijomJe400() {
        odbijeno("![a][r]\n\n[r]: https://x");
        odbijeno("![a][]\n\n[a]: https://x");
        odbijeno("![a]\n\n[a]: https://x");
        odbijeno("![a][r]\n\n> [r]:\n> https://x");
        odbijeno("- ![a][r]\n\n- [r]: <https://x> \"naslov\"");
        // inline slika iz GFS-a ne opravdava spoljnu definiciju kad postoji i referentna
        odbijeno("![a](" + GFS_A + ") ![b][r]\n\n[r]: https://x");
        // nepravilan inline oblik (smeće posle cilja) marked čita kao referencu ![a] -> definicija se proverava
        odbijeno("![a](" + GFS_A + " smece)\n\n[a]: https://x");
        odbijeno("![a]\r\n\r\n[a]: https://x");
    }

    @Test
    void referentnaSlikaIzGfsaProlazi() {
        assertEquals(Set.of(A), SlajdPP.slikeIzMarkdowna("![a][r]\n\n[r]: " + GFS_A));
        assertEquals(Set.of(A, B), SlajdPP.slikeIzMarkdowna("![a][r] ![b]\n\n[r]: <" + GFS_A + ">\n[b]:\n  " + GFS_B + " \"n\""));
        // referenca bez definicije je samo tekst
        assertEquals(Set.of(), SlajdPP.slikeIzMarkdowna("![a][nema]"));
    }

    @Test
    void obicanTekstSaSlikomLinkomIKodomProlazi() {
        assertEquals(Set.of(A, B), SlajdPP.slikeIzMarkdowna("Šema: ![Most](" + GFS_A + " \"Most\") i `kod[0]` pa "
                + "[sajt](https://gf.uns.ac.rs).\r\n\r\n- stavka ![](" + GFS_B + ")\n- [link](https://x)\n\n"
                + "Napomena [1]: vidi gore! [a](https://x)"));
    }

    @Test
    void slikaUKoduJeIpakOdbijena() {
        // namerno strože od marked-a (kod se ne prikazuje kao slika): proveru ne zanima gde je slika
        odbijeno("```\n![b](https://x.invalid/b.png)\n```");
        odbijeno("`![b](https://x.invalid/b.png)`");
    }

    @Test
    void definicijaLinkaBezReferentneSlikeNeSmeta() {
        assertEquals(Set.of(A), SlajdPP.slikeIzMarkdowna("![a](" + GFS_A + ") i [link][r]\n\n[r]: https://x"));
    }

    @Test
    void slikeSlajdaInfoIPitanjeSaOpcijama() {
        SlajdCmd info = new SlajdCmd(TipSlajda.INFO, "Naslov", "![a](" + GFS_A + ")", null, null, false, null, null, null);
        assertEquals(Set.of(A), SlajdPP.slikeSlajda(info));

        PitanjeCmd pitanje = new PitanjeCmd(TipPitanja.JEDAN_TACAN, "Šta je na slici? ![a](" + GFS_A + ")", null, null,
                List.of(new OpcijaCmd("![b](" + GFS_B + ")", true), new OpcijaCmd("ništa", false)),
                null, null, null, null, null, null, null, null);
        assertEquals(Set.of(A, B), SlajdPP.slikeSlajda(
                new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, pitanje, null, null)));
    }

    @Test
    void slikaVanGfsaUOpcijiJe400() {
        PitanjeCmd pitanje = new PitanjeCmd(TipPitanja.JEDAN_TACAN, "Koja?", null, null,
                List.of(new OpcijaCmd("![b](https://x.invalid/b.png)", true), new OpcijaCmd("ova", false)),
                null, null, null, null, null, null, null, null);
        SystemException e = assertThrows(SystemException.class, () -> SlajdPP.slikeSlajda(
                new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, pitanje, null, null)));
        assertEquals(SlajdPP.SLIKA_VAN_GFS, e.getMessage());
    }

    @Test
    void slikeSlajdaBezTeksta() {
        assertEquals(Set.of(), SlajdPP.slikeSlajda(null));
        assertEquals(Set.of(), SlajdPP.slikeSlajda(new SlajdCmd(TipSlajda.INFO, "Naslov", null, null, null, false, null,
                null, null)));
        assertEquals(Set.of(), SlajdPP.slikeSlajda(new SlajdCmd(TipSlajda.PLATNO, null, null, null, null, false, null,
                null, null)));
    }

    @Test
    void dozvoljenaSlikaJeTacnoUgovor() {
        assertEquals("^api/public/mediji/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                SlajdPP.DOZVOLJENA_SLIKA.pattern());
        assertTrue(SlajdPP.DOZVOLJENA_SLIKA.matcher(GFS_A).matches());
        assertFalse(SlajdPP.DOZVOLJENA_SLIKA.matcher(GFS_A + "\n").matches());
    }
}
