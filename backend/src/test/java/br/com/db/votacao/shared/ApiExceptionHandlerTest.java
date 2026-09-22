package br.com.db.votacao.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Testa o advice no fluxo MVC real, sem iniciar banco nem contexto Spring Boot. */
class ApiExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ExemploController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void acceptNaoSuportadoRetorna406EmVezDeErroInterno() throws Exception {
        mvc.perform(get("/exemplo").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.code").value("RESPOSTA_NAO_SUPORTADA"));
    }

    @Test
    void metodoInvalidoInformaAllowEProblemDetail() throws Exception {
        mvc.perform(delete("/exemplo"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METODO_NAO_PERMITIDO"));
    }

    @Test
    void corpoEmFormatoNaoSuportadoRetorna415() throws Exception {
        mvc.perform(post("/exemplo").contentType(MediaType.TEXT_PLAIN).content("conteúdo"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FORMATO_NAO_SUPORTADO"));
    }

    @RestController
    static class ExemploController {
        @GetMapping("/exemplo")
        Map<String, String> get() { return Map.of("status", "ok"); }

        @PostMapping("/exemplo")
        Map<String, String> post(@RequestBody Map<String, String> request) { return request; }
    }
}
