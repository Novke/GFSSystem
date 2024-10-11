package tri.novica.gfssystem.exceptions;

import lombok.Getter;

@Getter
public class SystemException extends RuntimeException{

    private Integer code;

    public SystemException(Integer code) {
        this.code = code;
    }

    public SystemException(String message, Integer code) {
        super(message);
        this.code = code;
    }

    public SystemException(String message, Throwable cause, Integer code) {
        super(message, cause);
        this.code = code;
    }

    public SystemException(Throwable cause, Integer code) {
        super(cause);
        this.code = code;
    }

    public SystemException() {
    }

    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

    public SystemException(Throwable cause) {
        super(cause);
    }
}
