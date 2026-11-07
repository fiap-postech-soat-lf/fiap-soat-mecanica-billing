package br.com.fiap.soat.mecanica.billing.adapters.in.web.pagamento;

import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.BuscarPagamentosPorOsUseCase;
import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.NovaCobrancaUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/pagamentos")
@Tag(name = "Pagamentos")
public class PagamentoController {

    private final BuscarPagamentosPorOsUseCase buscarPorOs;

    private final NovaCobrancaUseCase novaCobranca;

    public PagamentoController(BuscarPagamentosPorOsUseCase buscarPorOs, NovaCobrancaUseCase novaCobranca) {
        this.buscarPorOs = buscarPorOs;
        this.novaCobranca = novaCobranca;
    }

    @GetMapping("/por-os/{osId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ATENDENTE')")
    public PagamentosPorOsResponse buscarPorOs(@PathVariable UUID osId) {
        return PagamentosPorOsResponse.de(buscarPorOs.executar(osId));
    }

    /** Nova cobranca depois de uma recusa: so com o pagamento vigente RECUSADO (F6-h). */
    @PostMapping("/{osId}/nova-cobranca")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ATENDENTE')")
    public PagamentoResponse novaCobranca(@PathVariable UUID osId) {
        return PagamentoResponse.de(novaCobranca.executar(osId));
    }
}
