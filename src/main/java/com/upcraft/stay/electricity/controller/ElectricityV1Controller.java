package com.upcraft.stay.electricity.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.MeterReadingStatus;
import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.electricity.dto.ApproveReadingRequestDTO;
import com.upcraft.stay.electricity.dto.ConsumptionReportResponseDTO;
import com.upcraft.stay.electricity.dto.CreateMeterRequestDTO;
import com.upcraft.stay.electricity.dto.ElectricityBillResponseDTO;
import com.upcraft.stay.electricity.dto.GenerateElectricityBillsRequestDTO;
import com.upcraft.stay.electricity.dto.MeterReadingResponseDTO;
import com.upcraft.stay.electricity.dto.MeterResponseDTO;
import com.upcraft.stay.electricity.dto.RecordMeterReadingRequestDTO;
import com.upcraft.stay.electricity.dto.RejectReadingRequestDTO;
import com.upcraft.stay.electricity.dto.UpdateMeterRequestDTO;
import com.upcraft.stay.electricity.service.ElectricityBillingService;
import com.upcraft.stay.electricity.service.MeterReadingService;
import com.upcraft.stay.electricity.service.MeterService;

/**
 * Versioned REST endpoints for electricity meters, photo readings, approvals, split bills, and reports.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ElectricityV1Controller {

    private final MeterService meterService;
    private final MeterReadingService meterReadingService;
    private final ElectricityBillingService electricityBillingService;

    @GetMapping("/meters")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<MeterResponseDTO>>> listMeters(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) MeterScope meterScope,
            @RequestParam(required = false) MeterStatus meterStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "meterNumber") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        return ok("Meters fetched",
                meterService.listMeters(authentication.getName(), propertyId, meterScope, meterStatus,
                        search, page, size, sortBy, sortDirection));
    }

    @PostMapping("/meters")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<MeterResponseDTO>> createMeter(
            Authentication authentication,
            @Valid @RequestBody CreateMeterRequestDTO createRequestDTO
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success(
                        "Meter registered",
                        meterService.createMeter(authentication.getName(), createRequestDTO),
                        traceId()
                ));
    }

    @PutMapping("/meters/{meterId}")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<MeterResponseDTO>> updateMeter(
            Authentication authentication,
            @PathVariable UUID meterId,
            @Valid @RequestBody UpdateMeterRequestDTO updateRequestDTO
    ) {
        return ok("Meter configuration updated",
                meterService.updateMeter(authentication.getName(), meterId, updateRequestDTO));
    }

    @GetMapping("/meters/{meterId}/readings")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<MeterReadingResponseDTO>>> listMeterReadings(
            Authentication authentication,
            @PathVariable UUID meterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return ok("Meter reading history fetched",
                meterReadingService.listReadingsForMeter(authentication.getName(), meterId, page, size));
    }

    @GetMapping("/meter-readings")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<MeterReadingResponseDTO>>> listAllReadings(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) UUID meterId,
            @RequestParam(required = false) MeterReadingStatus readingStatus,
            @RequestParam(required = false) Integer billingYear,
            @RequestParam(required = false) Integer billingMonth,
            @RequestParam(required = false) Boolean unusualOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "readingDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        return ok("Readings fetched",
                meterReadingService.listReadings(authentication.getName(), propertyId, meterId, readingStatus,
                        billingYear, billingMonth, unusualOnly, page, size, sortBy, sortDirection));
    }

    @PostMapping("/meters/{meterId}/readings")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<MeterReadingResponseDTO>> recordReading(
            Authentication authentication,
            @PathVariable UUID meterId,
            @Valid @RequestBody RecordMeterReadingRequestDTO recordRequestDTO
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success(
                        "Meter reading recorded",
                        meterReadingService.recordReading(authentication.getName(), meterId, recordRequestDTO),
                        traceId()
                ));
    }

    @PostMapping("/meter-readings/{readingId}/approve")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<MeterReadingResponseDTO>> approveReading(
            Authentication authentication,
            @PathVariable UUID readingId,
            @RequestBody(required = false) ApproveReadingRequestDTO approveRequestDTO
    ) {
        return ok("Meter reading approved",
                meterReadingService.approveReading(authentication.getName(), readingId, approveRequestDTO));
    }

    @PostMapping("/meter-readings/{readingId}/reject")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<MeterReadingResponseDTO>> rejectReading(
            Authentication authentication,
            @PathVariable UUID readingId,
            @Valid @RequestBody RejectReadingRequestDTO rejectRequestDTO
    ) {
        return ok("Meter reading rejected",
                meterReadingService.rejectReading(authentication.getName(), readingId, rejectRequestDTO));
    }

    @PostMapping("/meter-readings/{readingId}/generate-bills")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<List<ElectricityBillResponseDTO>>> generateBills(
            Authentication authentication,
            @PathVariable UUID readingId,
            @Valid @RequestBody(required = false) GenerateElectricityBillsRequestDTO generateRequestDTO
    ) {
        List<ElectricityBillResponseDTO> bills = electricityBillingService.generateBillsForReading(
                authentication.getName(), readingId, generateRequestDTO
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Electricity bills generated", bills, traceId()));
    }

    @GetMapping("/electricity/bills")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<ElectricityBillResponseDTO>>> listBills(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID meterId,
            @RequestParam(required = false) Integer billingYear,
            @RequestParam(required = false) Integer billingMonth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        return ok("Electricity bills fetched",
                electricityBillingService.listBills(authentication.getName(), propertyId, tenantId, meterId,
                        billingYear, billingMonth, page, size, sortBy, sortDirection));
    }

    @GetMapping("/electricity/bills/mine")
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<ElectricityBillResponseDTO>>> listMyBills(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        return ok("My electricity bills fetched",
                electricityBillingService.listMyBills(authentication.getName(), page, size, sortBy, sortDirection));
    }

    @GetMapping("/electricity/consumption-report")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<ConsumptionReportResponseDTO>> getConsumptionReport(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) Integer billingYear,
            @RequestParam(required = false) Integer billingMonth
    ) {
        return ok("Consumption report computed",
                electricityBillingService.getConsumptionReport(
                        authentication.getName(), propertyId, billingYear, billingMonth
                ));
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> ok(String message, T payload) {
        return ResponseEntity.ok(ApiResponseDTO.success(message, payload, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}
