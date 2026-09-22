package br.com.db.votacao.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Verifique os campos informados.", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail parameterValidation(HandlerMethodValidationException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors.putIfAbsent(
                    parameter == null ? "parametro" : parameter, error.getDefaultMessage()));
        });
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Verifique os parâmetros informados.", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "JSON_INVALIDO",
                "Corpo JSON inválido. Confira os campos e os tipos; voto deve ser SIM ou NAO e duração deve ser um número inteiro.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail malformedParameter(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parâmetro com formato inválido.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail routeNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "ROTA_NAO_ENCONTRADA", "Recurso não encontrado.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        var response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (exception.getSupportedHttpMethods() != null) {
            response.allow(exception.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return response.body(problem(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_PERMITIDO", "Método HTTP não permitido para este recurso.", request));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> notAcceptable(HttpMediaTypeNotAcceptableException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.NOT_ACCEPTABLE, "RESPOSTA_NAO_SUPORTADA", "Esta API disponibiliza respostas em JSON.", request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail mediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FORMATO_NAO_SUPORTADO", "Envie o conteúdo como application/json.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        // Não registrar body, URI (pode conter CPF) ou mensagem de SQL com dados pessoais.
        log.error("Falha inesperada no processamento HTTP: tipo={}", exception.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Não foi possível processar a solicitação.", request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:votacao:erro:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setTitle(switch (status) {
            case BAD_REQUEST -> "Dados inválidos";
            case NOT_FOUND -> "Não encontrado";
            case CONFLICT -> "Conflito com o estado atual";
            case METHOD_NOT_ALLOWED -> "Método não permitido";
            case NOT_ACCEPTABLE -> "Formato de resposta não disponível";
            case UNSUPPORTED_MEDIA_TYPE -> "Formato não suportado";
            default -> "Erro interno";
        });
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
