package br.com.db.votacao.shared;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static DomainException pautaNaoEncontrada() {
        return new DomainException(HttpStatus.NOT_FOUND, "PAUTA_NAO_ENCONTRADA", "Pauta não encontrada.");
    }

    public static DomainException conflito(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }
}
