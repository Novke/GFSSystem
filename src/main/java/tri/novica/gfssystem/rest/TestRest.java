package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.test.tip.CreateTipTestaCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.service.TestService;

import java.util.List;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestRest {

    private final TestService testService;

    @PostMapping("/tip")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public TipTestaInfo createTipTesta(@RequestBody @Valid CreateTipTestaCmd cmd){
        return testService.createTipTesta(cmd);
    }

}
