package com.upcraft.stay.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Real database-backed KPI, chart, and actionable list payload for Owner & Manager dashboards.
 * Security deposits are tracked separately in {@code depositsHeld} and never included in rent revenue.
 */
public record StaffDashboardResponseDTO(
        int billingYear,
        int billingMonth,
        long totalProperties,
        long totalRooms,
        long totalBeds,
        long occupiedBeds,
        long vacantBeds,
        long reservedBeds,
        long maintenanceBeds,
        double occupancyPercent,
        BigDecimal rentCollectedMonth,
        BigDecimal rentOutstanding,
        BigDecimal electricityDue,
        BigDecimal depositsHeld,
        long openComplaintsCount,
        long pendingReadingsCount,
        List<MonthlyCollectionPointDTO> monthlyCollectionVsOutstanding,
        List<PropertyOccupancyPointDTO> propertyOccupancy,
        List<StatusBreakdownPointDTO> paymentStatusBreakdown,
        List<MonthlyElectricityPointDTO> electricityConsumptionSeries,
        List<DashboardInvoiceDueItemDTO> upcomingDues,
        List<DashboardInvoiceDueItemDTO> overdueTenants,
        List<DashboardPaymentItemDTO> recentPayments,
        List<DashboardTenancyEventDTO> recentMoveIns,
        List<DashboardTenancyEventDTO> recentMoveOuts,
        List<DashboardComplaintItemDTO> openComplaints,
        List<DashboardReadingItemDTO> readingsRequiringReview
) {
    public record MonthlyCollectionPointDTO(
            String monthLabel,
            BigDecimal collected,
            BigDecimal outstanding
    ) {}

    public record PropertyOccupancyPointDTO(
            UUID propertyId,
            String propertyName,
            long occupiedBeds,
            long vacantBeds,
            long totalBeds,
            double occupancyPercent
    ) {}

    public record StatusBreakdownPointDTO(
            String status,
            long count,
            BigDecimal amount
    ) {}

    public record MonthlyElectricityPointDTO(
            String monthLabel,
            BigDecimal unitsConsumed,
            BigDecimal billedAmount
    ) {}

    public record DashboardInvoiceDueItemDTO(
            UUID invoiceId,
            String invoiceNumber,
            String tenantName,
            String propertyName,
            String roomNumber,
            LocalDate dueDate,
            BigDecimal balanceDue,
            String invoiceStatus
    ) {}

    public record DashboardPaymentItemDTO(
            UUID paymentId,
            String receiptNumber,
            String tenantName,
            String propertyName,
            BigDecimal amount,
            String paymentMethod,
            LocalDate paidAt
    ) {}

    public record DashboardTenancyEventDTO(
            UUID tenancyId,
            String tenantName,
            String propertyName,
            String roomNumber,
            String bedCode,
            LocalDate eventDate,
            String tenancyStatus
    ) {}

    public record DashboardComplaintItemDTO(
            UUID complaintId,
            String title,
            String propertyName,
            String tenantName,
            String priority,
            String status
    ) {}

    public record DashboardReadingItemDTO(
            UUID readingId,
            String meterNumber,
            String propertyName,
            BigDecimal unitsConsumed,
            boolean unusualUsage,
            String readingStatus
    ) {}
}

