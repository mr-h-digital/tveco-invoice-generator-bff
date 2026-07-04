package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.*;
import co.za.tveco.bff.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public ApiResponse<Page<InvoiceDto>> getAll(
            @PageableDefault(size = 200, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.of(invoiceService.getAll(pageable));
    }

    @GetMapping("/next-number")
    public ApiResponse<NextInvoiceNumberDto> getNextNumber() {
        return ApiResponse.of(new NextInvoiceNumberDto(invoiceService.getNextInvoiceNumber()));
    }

    @GetMapping("/{id}")
    public ApiResponse<InvoiceDto> getById(@PathVariable UUID id) {
        return ApiResponse.of(invoiceService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvoiceDto> create(@Valid @RequestBody InvoiceRequest req) {
        return ApiResponse.of(invoiceService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<InvoiceDto> update(@PathVariable UUID id, @Valid @RequestBody InvoiceRequest req) {
        return ApiResponse.of(invoiceService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<InvoiceDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest req
    ) {
        return ApiResponse.of(invoiceService.updateStatus(id, req.status()));
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvoiceDto> duplicate(@PathVariable UUID id) {
        return ApiResponse.of(invoiceService.duplicate(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        invoiceService.delete(id);
    }
}
