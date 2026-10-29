package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.Ekran;
import tri.novica.gfssystem.entity.uzivo.Faza;
import tri.novica.gfssystem.entity.uzivo.Prikaz;
import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;
import tri.novica.gfssystem.entity.uzivo.Tema;

import java.util.List;

/**
 * Ceo snimak stanja izvođenja za nastavnika (konzola, prikaz za publiku). {@code indeks}: -1 prijava, n kraj;
 * {@code rezultat} uvek (i dok nije prikazan publici) sa tačnim odgovorima i sakrivenim tekstovima označenim, a
 * {@code rangLista} (top 10) uživo, sa poenima trenutne runde. Projektor prikazuje samo javne projekcije
 * {@code javniRezultat} i {@code javnaRangLista}: ono što dobija {@link JavnoStanje} ({@code rezultat} i
 * {@code rangLista}), izgrađeno istim kodom, pa važe ista pravila "ni ranije". Jedina razlika: {@code javniRezultat}
 * uvek ima tekstove opcija (sala ih vidi na platnu), a telefon ih ne dobija dok tekst nije dozvoljen.
 * {@code brojStavki}: broj koraka trenutnog slajda (stavke INFO liste ili KLIK animacije PLATNO slajda); ime ostaje zbog starog fronta.
 * {@code pocetakMs}: početak izvođenja u epoch ms (zona {@code Clock}-a, kao {@code rokMs}); štoperica u konzoli i
 * daljinskom meri od njega po serverskom satu (spec graditelja 9), nezavisno od vremenske zone pregledača.
 * {@code zadatak}: Zadatak slajd ({@link NastavnickiZadatak}), {@code null} van njega.
 * {@code brojOcekivanih}: imenilac "predalo x od y" (spec studentskog dela 4.7): neizbačeni učesnici, plus, za izvođenje
 * sa predavanja, studenti čekirani na tom predavanju (na času) koji još nisu učesnici niti su izbačeni iz ovog izvođenja.
 * {@code brojCekiranih}: broj studenata na času predavanja izvođenja, {@code null} kad izvođenje nema predavanje.
 * {@code javniRangTacnosti}: rang tačnosti za projektor (prvih {@code rangBroj} ili najviše 30, bez id-jeva, imena kao "Ana K.", trenutna runda tek posle TACAN), samo dok je
 * rang prikazan i nije rang takmičenja (u takmičenju {@code L} ostaje {@code javnaRangLista}); {@code rangPrikaz}: koji
 * rang je prikazan ({@code null} kad nijedan); {@code imaRangPredavanja}: izvođenje je sa predavanja koje ima još
 * izvođenja sa rundama (tab "Predavanje" u konzoli, {@code Shift+L}); {@code rangBroj}: koliko prvih redova ranga tačnosti
 * ide na projektor ({@code RANG_BROJ}), {@code null} = svi.
 */
public record NastavnickoStanje(IzvodjenjeInfo izvodjenje, long verzija, long serverVremeMs, Prikaz prikaz, int korak,
                                int brojStavki, int indeks, int brojSlajdova, SlajdDetails trenutniSlajd,
                                SlajdDetails sledeciSlajd, Faza faza, RundaInfo runda, boolean rezultatiPrikazani,
                                boolean tacanPrikazan, boolean rangListaPrikazana, Ekran ekran, boolean qrPrikazan,
                                TelefonPrikaz telefonPrikaz, boolean detaljiDozvoljeni, boolean takmicenje,
                                Rezultat rezultat, int brojOdgovora, int brojPovezanih, List<UcesnikStanje> ucesnici,
                                List<RangStavka> rangLista, Rezultat javniRezultat, List<RangStavka> javnaRangLista, Tema tema, long pocetakMs,
                                NastavnickiZadatak zadatak, int brojOcekivanih, Integer brojCekiranih,
                                List<RangTacnostiStavka> javniRangTacnosti, RangPrikaz rangPrikaz,
                                boolean imaRangPredavanja, Integer rangBroj) {

    /** Oblik pre ranga tačnosti: bez ranga na projektoru, bez ranga predavanja. */
    public NastavnickoStanje(IzvodjenjeInfo izvodjenje, long verzija, long serverVremeMs, Prikaz prikaz, int korak,
                             int brojStavki, int indeks, int brojSlajdova, SlajdDetails trenutniSlajd,
                             SlajdDetails sledeciSlajd, Faza faza, RundaInfo runda, boolean rezultatiPrikazani,
                             boolean tacanPrikazan, boolean rangListaPrikazana, Ekran ekran, boolean qrPrikazan,
                             TelefonPrikaz telefonPrikaz, boolean detaljiDozvoljeni, boolean takmicenje,
                             Rezultat rezultat, int brojOdgovora, int brojPovezanih, List<UcesnikStanje> ucesnici,
                             List<RangStavka> rangLista, Rezultat javniRezultat, List<RangStavka> javnaRangLista, Tema tema,
                             long pocetakMs, NastavnickiZadatak zadatak, int brojOcekivanih, Integer brojCekiranih) {
        this(izvodjenje, verzija, serverVremeMs, prikaz, korak, brojStavki, indeks, brojSlajdova, trenutniSlajd, sledeciSlajd,
                faza, runda, rezultatiPrikazani, tacanPrikazan, rangListaPrikazana, ekran, qrPrikazan, telefonPrikaz,
                detaljiDozvoljeni, takmicenje, rezultat, brojOdgovora, brojPovezanih, ucesnici, rangLista, javniRezultat,
                javnaRangLista, tema, pocetakMs, zadatak, brojOcekivanih, brojCekiranih, null, null, false, null);
    }

    /** Oblik pre čekiranja: {@code brojOcekivanih} = broj učesnika, {@code brojCekiranih} = {@code null}. */
    public NastavnickoStanje(IzvodjenjeInfo izvodjenje, long verzija, long serverVremeMs, Prikaz prikaz, int korak,
                             int brojStavki, int indeks, int brojSlajdova, SlajdDetails trenutniSlajd,
                             SlajdDetails sledeciSlajd, Faza faza, RundaInfo runda, boolean rezultatiPrikazani,
                             boolean tacanPrikazan, boolean rangListaPrikazana, Ekran ekran, boolean qrPrikazan,
                             TelefonPrikaz telefonPrikaz, boolean detaljiDozvoljeni, boolean takmicenje,
                             Rezultat rezultat, int brojOdgovora, int brojPovezanih, List<UcesnikStanje> ucesnici,
                             List<RangStavka> rangLista, Rezultat javniRezultat, List<RangStavka> javnaRangLista, Tema tema,
                             long pocetakMs, NastavnickiZadatak zadatak) {
        this(izvodjenje, verzija, serverVremeMs, prikaz, korak, brojStavki, indeks, brojSlajdova, trenutniSlajd, sledeciSlajd,
                faza, runda, rezultatiPrikazani, tacanPrikazan, rangListaPrikazana, ekran, qrPrikazan, telefonPrikaz,
                detaljiDozvoljeni, takmicenje, rezultat, brojOdgovora, brojPovezanih, ucesnici, rangLista, javniRezultat,
                javnaRangLista, tema, pocetakMs, zadatak, ucesnici == null ? 0 : ucesnici.size(), null);
    }

    public NastavnickoStanje(IzvodjenjeInfo izvodjenje, long verzija, long serverVremeMs, Prikaz prikaz, int korak,
                             int brojStavki, int indeks, int brojSlajdova, SlajdDetails trenutniSlajd,
                             SlajdDetails sledeciSlajd, Faza faza, RundaInfo runda, boolean rezultatiPrikazani,
                             boolean tacanPrikazan, boolean rangListaPrikazana, Ekran ekran, boolean qrPrikazan,
                             TelefonPrikaz telefonPrikaz, boolean detaljiDozvoljeni, boolean takmicenje,
                             Rezultat rezultat, int brojOdgovora, int brojPovezanih, List<UcesnikStanje> ucesnici,
                             List<RangStavka> rangLista, Rezultat javniRezultat, List<RangStavka> javnaRangLista, Tema tema,
                             long pocetakMs) {
        this(izvodjenje, verzija, serverVremeMs, prikaz, korak, brojStavki, indeks, brojSlajdova, trenutniSlajd, sledeciSlajd,
                faza, runda, rezultatiPrikazani, tacanPrikazan, rangListaPrikazana, ekran, qrPrikazan, telefonPrikaz,
                detaljiDozvoljeni, takmicenje, rezultat, brojOdgovora, brojPovezanih, ucesnici, rangLista, javniRezultat,
                javnaRangLista, tema, pocetakMs, null);
    }
}
