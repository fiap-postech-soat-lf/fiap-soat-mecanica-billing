package br.com.fiap.soat.mecanica.billing.adapters.in.web.orcamento;

import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.AprovarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.BuscarOrcamentoPorOsUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.BuscarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.RecusarOrcamentoUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orcamentos")
@Tag(name = "Orcamentos")
@PreAuthorize("hasAnyRole('CLIENTE', 'ATENDENTE')")
public class OrcamentoController {

    private final BuscarOrcamentoUseCase buscar;
    private final BuscarOrcamentoPorOsUseCase buscarPorOs;
    private final AprovarOrcamentoUseCase aprovar;
    private final RecusarOrcamentoUseCase recusar;

    public OrcamentoController(BuscarOrcamentoUseCase buscar, BuscarOrcamentoPorOsUseCase buscarPorOs,
                               AprovarOrcamentoUseCase aprovar, RecusarOrcamentoUseCase recusar) {
        this.buscar = buscar;
        this.buscarPorOs = buscarPorOs;
        this.aprovar = aprovar;
        this.recusar = recusar;
    }

    @GetMapping("/{id}")
    public OrcamentoResponse buscar(@PathVariable UUID id) {
        return OrcamentoResponse.de(buscar.executar(id));
    }

    @GetMapping("/por-os/{osId}")
    public OrcamentoResponse buscarPorOs(@PathVariable UUID osId) {
        return OrcamentoResponse.de(buscarPorOs.executar(osId));
    }

    @PatchMapping("/{id}/aprovar")
    public OrcamentoResponse aprovar(@PathVariable UUID id) {
        return OrcamentoResponse.de(aprovar.executar(id));
    }

    @PatchMapping("/{id}/recusar")
    public OrcamentoResponse recusar(@PathVariable UUID id,
                                     @RequestBody(required = false) @Valid RecusarOrcamentoRequest request) {
        return OrcamentoResponse.de(recusar.executar(id, request == null ? null : request.motivo()));
    }
}
