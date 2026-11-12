package com.upcraft.stay.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.enums.RoomStatus;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.room.entity.FloorEntity;

/**
 * Room entity preserving legacy {@code rooms} API compatibility while adding
 * multi-property, optional floor, and decimal pricing fields for Milestone 2.
 */
@Entity
@Table(name = "rooms")
@SQLRestriction("record_status <> 9")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private PropertyEntity property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private FloorEntity floorEntity;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private Integer floor;

    @Column(nullable = false)
    private String sharing;

    @Builder.Default
    @Column(name = "room_type", nullable = false, length = 40)
    private String roomType = "SHARING";

    @Column(nullable = false)
    private Integer capacity;

    @Builder.Default
    @Column(nullable = false)
    private Integer occupied = 0;

    @Column(nullable = false)
    private Double price;

    @Column(name = "base_monthly_rent", precision = 12, scale = 2)
    private BigDecimal baseMonthlyRent;

    @Builder.Default
    @Column(name = "default_deposit", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultDeposit = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "room_status", nullable = false, length = 30)
    private RoomStatus roomStatus = RoomStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_amenities", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "amenity")
    @Builder.Default
    private List<String> amenities = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Builder.Default
    @Column(name = "record_status", nullable = false)
    private Short recordStatus = AppConstants.STATUS_ACTIVE;

    @Column(name = "audit_tracker_id", length = 64)
    private String auditTrackerId;

    @Builder.Default
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
