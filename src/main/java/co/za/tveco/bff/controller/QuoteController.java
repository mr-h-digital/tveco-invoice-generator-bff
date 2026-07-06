package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.NextQuoteNumberDto;
import co.za.tveco.bff.dto.QuoteDto;
import co.za.tveco.bff.dto.QuoteRequest;
import co.za.tveco.bff.dto.QuoteStatusUpdateRequest;
import co.za.tveco.bff.service.QuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @GetMapping
    public ApiResponse<Page<QuoteDto>> getAll(
            @PageableDefault(size = 200, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.of(quoteService.getAll(pageable));
    }

    @GetMapping("/next-number")
    public ApiResponse<NextQuoteNumberDto> getNextNumber() {
        return ApiResponse.of(new NextQuoteNumberDto(quoteService.getNextQuoteNumber()));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuoteDto> getById(@PathVariable UUID id) {
        return ApiResponse.of(quoteService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuoteDto> create(@Valid @RequestBody QuoteRequest req) {
        return ApiResponse.of(quoteService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuoteDto> update(@PathVariable UUID id, @Valid @RequestBody QuoteRequest req) {
        return ApiResponse.of(quoteService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<QuoteDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody QuoteStatusUpdateRequest req
    ) {
        return ApiResponse.of(quoteService.updateStatus(id, req.status()));
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuoteDto> duplicate(@PathVariable UUID id) {
        return ApiResponse.of(quoteService.duplicate(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        quoteService.delete(id);
    }
}
