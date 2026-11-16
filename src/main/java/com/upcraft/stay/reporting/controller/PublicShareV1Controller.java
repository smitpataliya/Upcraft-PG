package com.upcraft.stay.reporting.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.reporting.dto.PublicShareViewResponseDTO;
import com.upcraft.stay.reporting.service.ShareLinkV1Service;

/**
 * Unauthenticated capability endpoint serving validated, non-expired share views and PDF downloads.
 */
@RestController
@RequestMapping("/api/v1/public/share")
@RequiredArgsConstructor
public class PublicShareV1Controller {

    private final ShareLinkV1Service shareLinkV1Service;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponseDTO<PublicShareViewResponseDTO>> getSharedView(@PathVariable String token) {
        PublicShareViewResponseDTO view = shareLinkV1Service.getPublicShareView(token);
        return ResponseEntity.ok(ApiResponseDTO.success("Shared resource loaded", view, traceId()));
    }

    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> exportSharedPdf(@PathVariable String token) {
        byte[] pdfBytes = shareLinkV1Service.exportPublicSharePdf(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("shared-document.pdf").build().toString())
                .body(pdfBytes);
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

