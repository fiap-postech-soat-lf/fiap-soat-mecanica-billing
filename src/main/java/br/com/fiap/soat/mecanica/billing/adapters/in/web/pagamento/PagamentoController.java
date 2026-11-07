package br.com.fiap.soat.mecanica.billing.adapters.in.web.pagamento;

import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.BuscarPagamentosPorOsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/pagamentos")
@Tag(name = "Pagamentos")
public class PagamentoController {

    private final BuscarPagamentosPorOsUseCase buscarPorOs;

    public PagamentoController(BuscarPagamentosPorOsUseCase buscarPorOs) {
        this.buscarPorOs = buscarPorOs;
    }

    @GetMapping("/por-os/{osId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ATENDENTE')")
    public PagamentosPorOsResponse buscarPorOs(@PathVariable UUID osId) {
        return PagamentosPorOsResponse.de(buscarPorOs.executar(osId));
    }
}
