package br.com.db.votacao.pauta;

import br.com.db.votacao.voto.ResultadoService;
import br.com.db.votacao.voto.VotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static br.com.db.votacao.pauta.PautaDtos.*;
import static br.com.db.votacao.voto.VotoDtos.*;

@RestController
@RequestMapping("/api/v1/pautas")
@Tag(name = "Pautas", description = "Cadastro, abertura de sessões, votos e apuração")
public class PautaController {
    private final PautaService pautas;
    private final SessaoService sessoes;
    private final VotoService votos;
    private final ResultadoService resultados;

    public PautaController(PautaService pautas, SessaoService sessoes, VotoService votos, ResultadoService resultados) {
        this.pautas = pautas;
        this.sessoes = sessoes;
        this.votos = votos;
        this.resultados = resultados;
    }

    @PostMapping
    @Operation(summary = "Cadastrar uma pauta")
    public ResponseEntity<PautaResponse> criar(@Valid @RequestBody CriarPauta request) {
        PautaResponse pauta = pautas.criar(request);
        return ResponseEntity.created(URI.create("/api/v1/pautas/" + pauta.id())).body(pauta);
    }

    @GetMapping
    @Operation(summary = "Listar pautas por data mais recente", description = "Busca parcial pelo título sem diferenciar maiúsculas. Tamanho máximo: 100.")
    public PaginaResponse<PautaResponse> listar(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página deve ser zero ou maior.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "O tamanho deve ser pelo menos 1.")
            @Max(value = 100, message = "O tamanho deve ser no máximo 100.") int size,
            @RequestParam(required = false) @Size(max = 140, message = "A busca deve ter no máximo 140 caracteres.") String busca) {
        return pautas.listar(page, size, busca);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar uma pauta e sua sessão")
    public PautaResponse buscar(@PathVariable @Positive(message = "O identificador deve ser positivo.") Long id) {
        return pautas.buscar(id);
    }

    @PostMapping("/{id}/sessoes")
    @Operation(summary = "Abrir sessão única de uma pauta", description = "Duração padrão: 1 minuto. Intervalo permitido: 1 a 1440 minutos. Não permite reabertura.")
    public ResponseEntity<SessaoResponse> abrir(
            @PathVariable @Positive(message = "O identificador deve ser positivo.") Long id,
            @Valid @RequestBody(required = false) AbrirSessao request) {
        return ResponseEntity.status(201).body(sessoes.abrir(id, request));
    }

    @PostMapping("/{id}/votos")
    @Operation(summary = "Registrar voto imutável", description = "Apenas SIM ou NAO, uma vez por associado/pauta e antes do encerramento. CPF opcional aciona a simulação aleatória; inapto ou inválido retorna 404.")
    public ResponseEntity<VotoResponse> votar(
            @PathVariable @Positive(message = "O identificador deve ser positivo.") Long id,
            @Valid @RequestBody RegistrarVoto request) {
        return ResponseEntity.status(201).body(votos.votar(id, request));
    }

    @GetMapping("/{id}/resultado")
    @Operation(summary = "Apurar resultado", description = "Durante a sessão, resultado parcial EM_ANDAMENTO. Depois: APROVADA, REJEITADA, EMPATE ou SEM_VOTOS.")
    public ResultadoResponse resultado(@PathVariable @Positive(message = "O identificador deve ser positivo.") Long id) {
        return resultados.apurar(id);
    }
}
