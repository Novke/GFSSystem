package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.Ekran;
import tri.novica.gfssystem.entity.uzivo.Faza;
import tri.novica.gfssystem.entity.uzivo.Prikaz;
import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;

import java.util.List;

/**
 * Ceo snimak stanja izvođenja za nastavnika (konzola, prikaz za publiku). {@code indeks}: -1 prijava, n kraj;
 * {@code rezultat} uvek (i dok nije prikazan publici) sa tačnim odgovorima i sakrivenim tekstovima označenim.
 */
public record NastavnickoStanje(IzvodjenjeInfo izvodjenje, long verzija, long serverVremeMs, Prikaz prikaz, int korak,
                                int brojStavki, int indeks, int brojSlajdova, SlajdDetails trenutniSlajd,
                                SlajdDetails sledeciSlajd, Faza faza, RundaInfo runda, boolean rezultatiPrikazani,
                                boolean tacanPrikazan, boolean rangListaPrikazana, Ekran ekran, boolean qrPrikazan,
                                TelefonPrikaz telefonPrikaz, boolean detaljiDozvoljeni, boolean takmicenje,
                                Rezultat rezultat, int brojOdgovora, int brojPovezanih, List<UcesnikStanje> ucesnici,
                                List<RangStavka> rangLista) {
}
