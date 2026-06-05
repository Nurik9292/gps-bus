package biz.ugur.busroutebackend.client.application.service;

import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;

@ExtendWith(MockitoExtension.class)
class ClientLookupServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientLookupService service;

    @Test
    void findByIds_mapsClientsByIdWithPhone() {
        Client client = Client.create("Иван", "+99361222333", Platform.ANDROID);
        String id = client.getId().getValue();
        when(clientRepository.findByIds(anyCollection())).thenReturn(Flux.just(client));

        StepVerifier.create(service.findByIds(List.of(id)))
                .assertNext(map -> {
                    assertThat(map).containsKey(id);
                    assertThat(map.get(id).phone()).isEqualTo("+99361222333");
                    assertThat(map.get(id).name()).isEqualTo("Иван");
                })
                .verifyComplete();
    }

    @Test
    void findByIds_emptyInput_shortCircuitsWithoutQuery() {
        StepVerifier.create(service.findByIds(List.of()))
                .assertNext(map -> assertThat(map).isEmpty())
                .verifyComplete();

        verify(clientRepository, never()).findByIds(anyCollection());
    }
}
