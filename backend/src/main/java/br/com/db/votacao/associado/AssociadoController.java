package br.com.db.votacao.associado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/associados")
@Tag(name = "Elegibilidade", description = "Bônus: integração fictícia, aleatória e sem dados externos")
public class AssociadoController {
    private final ElegibilidadeService elegibilidade;

    public AssociadoController(ElegibilidadeService elegibilidade) {
        this.elegibilidade = elegibilidade;
    }

    @GetMapping("/{cpf}/elegibilidade")
    @Operation(summary = "Simular elegibilidade por CPF", description = "11 dígitos geram aleatoriamente apto (200), inapto (404) ou inválido (404 ProblemDetail). Nenhum CPF é registrado ou enviado a terceiros.")
    public ResponseEntity<ElegibilidadeResponse> consultar(@PathVariable String cpf) {
        return elegibilidade.consultar(cpf) == SituacaoCpf.APTO
                ? ResponseEntity.ok(new ElegibilidadeResponse("ABLE_TO_VOTE"))
                : ResponseEntity.status(404).body(new ElegibilidadeResponse("UNABLE_TO_VOTE"));
    }

    public record ElegibilidadeResponse(String status) { }
}
