package tri.novica.gfssystem.service.anketa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.anketa.AnketeSabloniInfo;
import tri.novica.gfssystem.dto.anketa.FolderAnkete;
import tri.novica.gfssystem.dto.anketa.SablonAnkete;
import tri.novica.gfssystem.dto.anketa.SablonAnketeCmd;
import tri.novica.gfssystem.entity.anketa.BojaPrecice;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;
import tri.novica.gfssystem.exceptions.SystemException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sačuvana pitanja, folderi i prečice brze ankete nad pravom bazom (spec brze ankete 4 i 5). Tabele su globalne (nisu
 * vezane za predmet), pa test briše sve šablone i foldere pre i posle sebe.
 */
@SpringBootTest
class AnketaSablonServiceDbTest {

    static final List<String> DA_NE = List.of("Da", "Ne");

    @Autowired AnketaSablonService sabloni;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void ocisti() {
        jdbc.update("delete from ankete_sabloni");
        jdbc.update("delete from ankete_folderi");
    }

    static SablonAnketeCmd cmd(Long folderId, String pitanje, PublikaAnkete publika, boolean precica) {
        return new SablonAnketeCmd(folderId, pitanje, DA_NE, false, publika, 60, 24, precica, null, null, null, false);
    }

    @Test
    void sablonCuvaSamoPoljaSvojePublikeIZamenjujeSeCeo() {
        SablonAnkete g = sabloni.kreiraj(new SablonAnketeCmd(null, "  Kolokvijum: koji datum?  ", List.of("Opcija 1",
                "Opcija 2"), false, PublikaAnkete.GRUPA, 60, 24, true, " 📅 ", BojaPrecice.VISNJA, "Kolokvijum datum", false));
        assertEquals("Kolokvijum: koji datum?", g.pitanje());
        assertNull(g.trajanjeSekunde(), "trajanje važi samo za anketu na času");
        assertEquals(24, g.rokSati());
        assertEquals("📅", g.ikonica());
        assertEquals(BojaPrecice.VISNJA, g.boja());
        assertTrue(g.precica());
        assertEquals(1, g.precicaRb());
        assertEquals(0, g.brojPokretanja());

        SablonAnkete n = sabloni.izmeni(g.id(), new SablonAnketeCmd(null, "Pauza?", DA_NE, null, null, 60, 24, null, "",
                null, null, null));
        assertEquals(PublikaAnkete.NA_CASU, n.publika(), "null = NA_CASU");
        assertEquals(60, n.trajanjeSekunde());
        assertNull(n.rokSati());
        assertFalse(n.precica(), "cela zamena: bez precica nije prečica");
        assertNull(n.precicaRb());
        assertNull(n.ikonica());
        assertNull(n.boja());
        assertFalse(n.viseOdgovora());

        ocekuj(400, AnketaPravila.PITANJE, () -> sabloni.kreiraj(cmd(null, " ", null, false)));
        ocekuj(400, AnketaPravila.TRAJANJE, () -> sabloni.kreiraj(new SablonAnketeCmd(null, "P?", DA_NE, false, null, 5, null,
                false, null, null, null, false)));
        ocekuj(400, AnketaPravila.ROK_SATI, () -> sabloni.kreiraj(new SablonAnketeCmd(null, "P?", DA_NE, false,
                PublikaAnkete.GRUPA, null, 400, false, null, null, null, false)));
        ocekuj(400, AnketaPravila.KRATAK_NAZIV, () -> sabloni.kreiraj(new SablonAnketeCmd(null, "P?", DA_NE, false, null,
                null, null, true, null, null, "k".repeat(31), false)));
        ocekuj(404, AnketaSablonService.FOLDER_NIJE_PRONADJEN, () -> sabloni.kreiraj(cmd(-1L, "P?", null, false)));
        ocekuj(404, AnketaSablonService.SABLON_NIJE_PRONADJEN, () -> sabloni.izmeni(-1L, cmd(null, "P?", null, false)));
        ocekuj(404, AnketaSablonService.SABLON_NIJE_PRONADJEN, () -> sabloni.obrisi(-1L));
        assertEquals(1, sabloni.sve().sabloni().size());
    }

    @Test
    void precicePrveNajviseDvanaestIRedosled() {
        List<Long> ids = new ArrayList<>();
        Long obican = sabloni.kreiraj(cmd(null, "Obično", null, false)).id();
        for (int i = 1; i <= AnketaSablonService.NAJVISE_PRECICA; i++) {
            SablonAnkete s = sabloni.kreiraj(cmd(null, "Prečica " + i, null, true));
            assertEquals(i, s.precicaRb());
            ids.add(s.id());
        }
        ocekuj(409, AnketaSablonService.PREVISE_PRECICA, () -> sabloni.kreiraj(cmd(null, "Trinaesta", null, true)));
        ocekuj(409, AnketaSablonService.PREVISE_PRECICA, () -> sabloni.izmeni(obican, cmd(null, "Obično", null, true)));
        SablonAnkete ista = sabloni.izmeni(ids.get(2), cmd(null, "Prečica 3, izmenjena", null, true));
        assertEquals(3, ista.precicaRb(), "izmena postojeće prečice ne menja mesto ni ne broji se dvaput");

        AnketeSabloniInfo info = sabloni.poredjajPrecice(List.of(ids.get(5), obican));
        assertEquals(List.of(ids.get(5), obican), info.sabloni().subList(0, 2).stream().map(SablonAnkete::id).toList());
        assertEquals(List.of(1, 2), info.sabloni().subList(0, 2).stream().map(SablonAnkete::precicaRb).toList());
        assertEquals(2, info.sabloni().stream().filter(SablonAnkete::precica).count(), "ostali prestaju da budu prečice");
        assertEquals(13, info.sabloni().size());

        ocekuj(400, AnketaSablonService.NEISPRAVNE_PRECICE, () -> sabloni.poredjajPrecice(List.of(obican, obican)));
        ocekuj(400, AnketaSablonService.NEISPRAVNE_PRECICE, () -> sabloni.poredjajPrecice(List.of(-1L)));
        List<Long> previse = new ArrayList<>(ids);
        previse.add(obican);
        ocekuj(409, AnketaSablonService.PREVISE_PRECICA, () -> sabloni.poredjajPrecice(previse));
        assertEquals(0, sabloni.poredjajPrecice(List.of()).sabloni().stream().filter(SablonAnkete::precica).count());
    }

    @Test
    void folderiJedinstveniRedosledIBrisanje() {
        FolderAnkete tok = sabloni.kreirajFolder(" Tok časa ");
        assertEquals("Tok časa", tok.naziv());
        FolderAnkete org = sabloni.kreirajFolder("Organizacija predmeta");
        assertEquals(tok.rb() + 1, org.rb());
        ocekuj(409, AnketaSablonService.ISTI_FOLDER, () -> sabloni.kreirajFolder("TOK ČASA"));
        assertEquals(tok.id(), sabloni.folderPoNazivu("tok časa").id(), "postojeći bez obzira na velika i mala slova");
        ocekuj(409, AnketaSablonService.ISTI_FOLDER, () -> sabloni.preimenujFolder(org.id(), "Tok Časa"));
        assertEquals("Tok časa!", sabloni.preimenujFolder(tok.id(), "Tok časa!").naziv());
        ocekuj(400, AnketaPravila.NAZIV_FOLDERA, () -> sabloni.kreirajFolder(" "));

        ocekuj(400, AnketaSablonService.NEISPRAVAN_REDOSLED_FOLDERA, () -> sabloni.poredjajFoldere(List.of(org.id())));
        ocekuj(400, AnketaSablonService.NEISPRAVAN_REDOSLED_FOLDERA,
                () -> sabloni.poredjajFoldere(List.of(org.id(), org.id())));
        AnketeSabloniInfo info = sabloni.poredjajFoldere(List.of(org.id(), tok.id()));
        assertEquals(List.of(org.id(), tok.id()), info.folderi().stream().map(FolderAnkete::id).toList());

        SablonAnkete bez = sabloni.kreiraj(cmd(null, "Bez foldera", null, false));
        SablonAnkete u1 = sabloni.kreiraj(cmd(tok.id(), "U folderu 1", null, false));
        SablonAnkete u2 = sabloni.kreiraj(cmd(tok.id(), "U folderu 2", null, false));
        assertEquals(u1.rb() + 1, u2.rb());
        assertEquals(2, sabloni.sve().folderi().stream().filter(f -> f.id().equals(tok.id())).findFirst().orElseThrow()
                .brojSablona());

        sabloni.obrisiFolder(tok.id());
        SablonAnkete p1 = sabloni.sablon(u1.id());
        SablonAnkete p2 = sabloni.sablon(u2.id());
        assertNull(p1.folderId());
        assertTrue(p1.rb() > bez.rb() && p2.rb() > p1.rb(), "na kraj liste bez foldera, istim redom");
        ocekuj(404, AnketaSablonService.FOLDER_NIJE_PRONADJEN, () -> sabloni.obrisiFolder(tok.id()));
        assertEquals(List.of(org.id()), sabloni.sve().folderi().stream().map(FolderAnkete::id).toList());
    }

    @Test
    void klodCuvaPoNazivuFolderaIPoTekstuPitanja() {
        SablonAnkete prvi = sabloni.sacuvajZaKloda(null, "Tok časa", cmd(null, "Da li vam treba pauza?", null, true));
        assertNotNull(prvi.folderId());
        SablonAnkete opet = sabloni.sacuvajZaKloda(null, "tok časa", new SablonAnketeCmd(null, "DA LI VAM TREBA PAUZA?",
                List.of("Da, 10 min", "Da, 15 min", "Ne"), false, null, 60, null, true, "☕", BojaPrecice.PLAVA, "Pauza?", true));
        assertEquals(prvi.id(), opet.id(), "isto pitanje: izmena, ne duplikat");
        assertEquals(prvi.folderId(), opet.folderId());
        assertEquals(prvi.precicaRb(), opet.precicaRb());
        assertEquals(3, opet.opcije().size());
        assertEquals(1, sabloni.sve().sabloni().size());
        assertEquals(1, sabloni.sve().folderi().size());

        SablonAnkete bez = sabloni.sacuvajZaKloda(opet.id(), "  ", cmd(null, "Pauza (novo)?", null, false));
        assertEquals(opet.id(), bez.id());
        assertNull(bez.folderId(), "prazan folder = bez foldera");
        assertEquals(opet.folderId(), sabloni.folderZaKloda(null, "TOK ČASA").id());
        assertEquals("Kraj časa", sabloni.folderZaKloda(opet.folderId(), "Kraj časa").naziv());
    }

    @Test
    void graniceSablonaIFoldera() {
        LocalDateTime t = LocalDateTime.of(2030, 1, 1, 0, 0);
        List<Object[]> redovi = new ArrayList<>();
        for (int i = 0; i < AnketaSablonService.NAJVISE_SABLONA; i++) {
            redovi.add(new Object[]{"Masovno " + i, "[\"Da\",\"Ne\"]", Timestamp.valueOf(t), Timestamp.valueOf(t)});
        }
        jdbc.batchUpdate("insert into ankete_sabloni (pitanje, opcije, kreirano, izmenjeno) values (?, ?, ?, ?)", redovi);
        ocekuj(409, AnketaSablonService.PREVISE_SABLONA, () -> sabloni.kreiraj(cmd(null, "Još jedan", null, false)));
        assertEquals(DA_NE, sabloni.sve().sabloni().getFirst().opcije(), "JSON kolona se čita");
        for (int i = 0; i < AnketaSablonService.NAJVISE_FOLDERA; i++) sabloni.kreirajFolder("F" + i);
        ocekuj(409, AnketaSablonService.PREVISE_FOLDERA, () -> sabloni.kreirajFolder("Previše"));
        ocekuj(409, AnketaSablonService.PREVISE_FOLDERA, () -> sabloni.folderPoNazivu("Previše"));
        assertEquals("F3", sabloni.folderPoNazivu("f3").naziv(), "postojeći se nalazi i na granici");
    }

    static void ocekuj(int kod, String poruka, Executable e) {
        SystemException ex = assertThrows(SystemException.class, e);
        assertEquals(kod, ex.getCode(), poruka);
        assertEquals(poruka, ex.getMessage());
    }
}
