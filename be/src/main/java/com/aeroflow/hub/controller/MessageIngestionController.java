package com.aeroflow.hub.controller;

import com.aeroflow.hub.service.MessageIngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
public class MessageIngestionController {

    public static final int MAX_PAYLOAD_LENGTH = 1_000_000;

    private final MessageIngestionService ingestionService;

    @PostMapping("/sita")
    public ResponseEntity<MessageIngestionService.IngestionResult> ingestSita(
            Authentication authentication,
            @Valid @RequestBody IngestionRequest request
    ) {
        var result = ingestionService.ingestSita(request.payload(), authentication.getName());
        return ResponseEntity.status(result.quarantined() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(result);
    }

    @PostMapping("/ssim")
    public ResponseEntity<MessageIngestionService.IngestionResult> ingestSsim(
            Authentication authentication,
            @Valid @RequestBody IngestionRequest request
    ) {
        var result = ingestionService.ingestSsim(request.payload(), authentication.getName());
        return ResponseEntity.status(result.quarantined() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(result);
    }

    @GetMapping("/quarantine")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public Page<MessageIngestionService.QuarantineSummary> listQuarantine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ingestionService.findQuarantine(PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/quarantine/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageIngestionService.QuarantineDetails getQuarantineEntry(@PathVariable UUID id) {
        return ingestionService.findQuarantineEntry(id);
    }

    public record IngestionRequest(
            @NotBlank @Size(max = MAX_PAYLOAD_LENGTH) String payload
    ) {
    }
}
