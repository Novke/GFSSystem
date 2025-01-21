package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.ocenjivanje.RezultatiStudentaInfo;
import tri.novica.gfssystem.dto.predmet.CreatePredmetCmd;
import tri.novica.gfssystem.dto.predmet.GetPredispitnePoeneCmd;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.ocenjivanje.MaxPoeniStudentaNaTestuInfo;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PredmetService {

    private final PredmetRepository predmetRepository;
    private final TipTestaRepository tipTestaRepository;
    private final PolaganjeRepository polaganjeRepository;
    private final GrupaRepository grupaRepository;
    private final AktivnostRepository aktivnostRepository;
    private final DomaciRepository domaciRepository;
    private final ModelMapper mapper;

    public List<PredmetInfo> findAll() {
        return predmetRepository.findAll()
                .stream().map(
                        predmet -> mapper.map(predmet, PredmetInfo.class)
                ).toList();
    }

    public PredmetInfo save(CreatePredmetCmd predmetCmd) {
        return mapper.map(predmetRepository.save(
                mapper.map(predmetCmd, Predmet.class)
        ), PredmetInfo.class);
    }

    public PredmetInfo findById(Long id) {
        return mapper.map(
                predmetRepository.findById(id)
                        .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + id, 404)),
                PredmetInfo.class
        );
    }

    public List<TipTestaInfo> findTipovePredmeta(Long id) {
        Predmet predmet = predmetRepository.findById(id)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + id, 404));

        return tipTestaRepository.findAllByPredmetAndAktivanTrue(predmet)
                .stream().map(tip -> mapper.map(tip, TipTestaInfo.class)).toList();
    }

    public List<RezultatiStudentaInfo> getUkupnePoene(Long id, GetPredispitnePoeneCmd cmd) {
        Predmet predmet = predmetRepository.findById(id)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + id, 404));

        List<TipTesta> tipovi = tipTestaRepository.findAllByPredmetAndAktivanTrue(predmet);
        Grupa grupa = grupaRepository.findByIdFetchStudents(cmd.getGrupaId())
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + id, 404));

        //nadji sve aktivnosti
        List<Aktivnost> aktivnosti = aktivnostRepository.findAllByStudentGrupaAndPredavanjePredmet(grupa, predmet);

        //nadji sve domace
        List<Domaci> domaci = domaciRepository.findAllByGrupaAndPredmetOrderByDatumAsc(grupa, predmet);
        List<UradjenDomaci> uradjeniDomaci = new ArrayList<>();

        domaci.forEach(d -> uradjeniDomaci.addAll(d.getUradjeniDomaci()));

        //nadji sve testove, po tipu testa
        List<RezultatiStudentaInfo> vrati = new ArrayList<>();
        List<Student> students = new ArrayList<>(grupa.getStudenti());

        for (Student s : students){
            RezultatiStudentaInfo rezultati = new RezultatiStudentaInfo(mapper.map(s, StudentInfo.class));

            //poeni sa aktivnosti
            double aktivnostPoeni = 0;
            for (Aktivnost a : aktivnosti.stream().filter(a -> Objects.equals(a.getStudent().getId(), s.getId())).toList()){
                switch (a.getTip()){
                    case PRISUSTVO -> aktivnostPoeni+=cmd.getVrednostPrisustvo();
                    case ZADATAK -> aktivnostPoeni+=cmd.getVrednostAktivnost();
                    case SA_ZVEZDICOM -> aktivnostPoeni+=cmd.getVrednostZvezdica();
                }
            }
            rezultati.setPoeniAktivnost(aktivnostPoeni);

            //poeni sa domacih
            double domaciPoeni = 0;
            for (UradjenDomaci d : uradjeniDomaci.stream().filter(d -> Objects.equals(d.getStudent().getId(), s.getId())).toList()){
                domaciPoeni+= cmd.getVrednostDomaci();
                domaciPoeni+= (cmd.getVrednostDomaciMaxPoena() * d.getBodovi()) / 10; //10 je max prema domacima
            }
            rezultati.setPoenidomaci(domaciPoeni);

            //poeni sa testova
            for (TipTesta tip : tipovi){
                Double poeni = polaganjeRepository.findMaxPoeniByStudentAndTipTesta(s.getId(), tip.getId());
                if (poeni != null){
                    MaxPoeniStudentaNaTestuInfo rez = new MaxPoeniStudentaNaTestuInfo(mapper.map(tip, TipTestaInfo.class), poeni);
                    rezultati.getRezultati().add(rez);
                }
            }
            rezultati.izracunajUkupno();

            vrati.add(rezultati);
        }

        return vrati;
    }
}
