package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.ClientDto;
import co.za.tveco.bff.dto.ClientRequest;
import co.za.tveco.bff.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public ApiResponse<List<ClientDto>> getAll() {
        return ApiResponse.of(clientService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ClientDto> getById(@PathVariable UUID id) {
        return ApiResponse.of(clientService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ClientDto> create(@Valid @RequestBody ClientRequest req) {
        return ApiResponse.of(clientService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ClientDto> update(@PathVariable UUID id, @Valid @RequestBody ClientRequest req) {
        return ApiResponse.of(clientService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        clientService.delete(id);
    }
}
