package br.com.fiap.soat.mecanica.billing.adapters.in.web.pagamento;

import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.BuscarPagamentosPorOsUseCase.Resultado;

import java.util.List;

public record PagamentosPorOsResponse(PagamentoResponse vigente, List<PagamentoResponse> historico) {

    public static PagamentosPorOsResponse de(Resultado r) {
        return new PagamentosPorOsResponse(PagamentoResponse.de(r.vigente()),
                r.historico().stream().map(PagamentoResponse::de).toList());
    }
}
