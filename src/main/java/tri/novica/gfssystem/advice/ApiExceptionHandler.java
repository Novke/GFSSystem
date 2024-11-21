package tri.novica.gfssystem.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.LocalDateTime;
import java.util.List;

@ControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiException handleUnexpectedErrors(Exception ex){
        log.error("Unexpected error", ex);
        return new ApiException(ex.getMessage(), LocalDateTime.now());
    }

    @ExceptionHandler(SystemException.class)
    ResponseEntity<ApiException> handleSystemExceptions(SystemException ex){
        Integer code = ex.getCode();
        code = code == null ? 400 : code;
        ApiException apiException =  new ApiException(ex.getMessage(), LocalDateTime.now());

        log.info("Exception handled. Code: {}, Reason: {}", ex.getCode(), ex.getMessage(), ex);

        return ResponseEntity.status(code).body(apiException);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiException> handleInvalidArgument(MethodArgumentNotValidException ex) {
        List<String> errorMessages = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage) // Samo poruka greške
                .toList();

        String userFriendlyMessage = String.join(", ", errorMessages);

        ApiException apiException = new ApiException(userFriendlyMessage, LocalDateTime.now());

        log.info("Invalid arguments: {}", userFriendlyMessage, ex);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiException);
    }


}
