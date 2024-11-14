package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.test.CreateTestCmd;
import tri.novica.gfssystem.dto.test.TestInfo;
import tri.novica.gfssystem.dto.test.tip.CreateTipTestaCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.service.TestService;

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

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public TestInfo createTest(@RequestBody @Valid CreateTestCmd cmd){
        return testService.createTest(cmd);
    }

}
