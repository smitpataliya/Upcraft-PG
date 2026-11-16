package com.upcraft.stay.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resident-isolated dashboard summary derived from real tenancy, invoice, electricity, and operations tables.
 */
public record TenantDashboardResponseDTO(
        UUID tenantId,
        UUID propertyId,
        String propertyName,
        String roomNumber,
        String bedCode,
        BigDecimal agreedMonthlyRent,
        BigDecimal depositHeld,
        LocalDate moveInDate,
        BigDecimal totalOutstanding,
        BigDecimal electricityDue,
        long unpaidInvoicesCount,
        long openComplaintsCount,
        long unreadAnnouncementsCount,
        long unreadNotificationsCount,
        List<StaffDashboardResponseDTO.DashboardInvoiceDueItemDTO> upcomingOrOverdueBills,
        List<StaffDashboardResponseDTO.DashboardPaymentItemDTO> recentPayments
) {
}

