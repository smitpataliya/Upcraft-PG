package com.upcraft.stay.tenant.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.upcraft.stay.billing.service.DepositService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.PropertyStatus;
import com.upcraft.stay.common.enums.RefundStatus;
import com.upcraft.stay.common.enums.RoomStatus;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.enums.TenantStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.organization.service.InvitationService;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.mapper.BedMapper;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.dto.MoveInRequestDTO;
import com.upcraft.stay.tenant.dto.MoveOutRequestDTO;
import com.upcraft.stay.tenant.dto.TenancyResponseDTO;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.mapper.TenancyMapper;
import com.upcraft.stay.tenant.repository.TenancyRepository;
import com.upcraft.stay.tenant.repository.TenantRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenancyServiceImplTest {

    @Mock
    private AccessGuardService accessGuardService;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenancyRepository tenancyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvitationService invitationService;

    @Mock
    private BedMapper bedMapper;

    @Mock
    private DepositService depositService;

    private TenancyServiceImpl tenancyService;

    @BeforeEach
    void setUp() {
        TenancyLifecycleHelper lifecycleHelper = new TenancyLifecycleHelper(
                bedRepository,
                roomRepository,
                tenantRepository,
                tenancyRepository,
                userRepository,
                invitationService
        );
        TenancyMapper manualMapper = tenancyEntity -> new TenancyResponseDTO(
                tenancyEntity.getId(),
                tenancyEntity.getOrganization().getId(),
                tenancyEntity.getProperty().getId(),
                tenancyEntity.getProperty().getName(),
                tenancyEntity.getTenant().getId(),
                tenancyEntity.getTenant().getFullName(),
                tenancyEntity.getTenant().getPhone(),
                tenancyEntity.getRoom().getId(),
                tenancyEntity.getRoom().getNumber(),
                tenancyEntity.getBed().getId(),
                tenancyEntity.getBed().getBedCode(),
                tenancyEntity.getTenancyStatus(),
                tenancyEntity.getMoveInDate(),
                tenancyEntity.getExpectedMoveOutDate(),
                tenancyEntity.getActualMoveOutDate(),
                tenancyEntity.getAgreedMonthlyRent(),
                tenancyEntity.getSecurityDepositAmount(),
                tenancyEntity.getDepositReceivedAmount(),
                tenancyEntity.getRentDueDay(),
                tenancyEntity.getInitialMeterReading(),
                tenancyEntity.getFinalMeterReading(),
                tenancyEntity.getUnpaidDuesSettled(),
                tenancyEntity.getDeductionsAmount(),
                tenancyEntity.getDeductionReason(),
                tenancyEntity.getRefundableDepositAmount(),
                tenancyEntity.getRefundStatus(),
                tenancyEntity.getAgreementStartDate(),
                tenancyEntity.getAgreementEndDate(),
                tenancyEntity.getMoveInNotes(),
                tenancyEntity.getMoveOutNotes(),
                tenancyEntity.getCreatedAt()
        );
        tenancyService = new TenancyServiceImpl(
                accessGuardService,
                bedRepository,
                tenancyRepository,
                lifecycleHelper,
                bedMapper,
                manualMapper,
                depositService,
                new AppProperties()
        );
    }

    @Test
    void moveIn_whenBedVacant_allocatesBedAndMarksOccupied() {
        UUID propertyId = UUID.randomUUID();
        UUID bedId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).email("owner@pg.in").role(Role.PG_OWNER).active(true).build();
        PropertyEntity property = buildProperty(propertyId);
        BedEntity bed = buildBed(bedId, property, BedStatus.VACANT);

        when(accessGuardService.loadActiveUser("owner@pg.in")).thenReturn(owner);
        when(bedRepository.findLockedById(bedId)).thenReturn(Optional.of(bed));
        when(accessGuardService.requirePropertyManagementAccess(owner, propertyId, "TENANTS")).thenReturn(property);
        when(tenancyRepository.existsByBedIdAndTenancyStatusIn(any(), any())).thenReturn(false);
        when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(inv -> {
            TenantEntity saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(tenancyRepository.save(any(TenancyEntity.class))).thenAnswer(inv -> {
            TenancyEntity saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(bedRepository.countByRoomIdAndBedStatus(bed.getRoom().getId(), BedStatus.OCCUPIED)).thenReturn(1L);

        MoveInRequestDTO request = new MoveInRequestDTO(
                bedId, null, "Rohan Verma", "9876543210", "rohan@pg.in",
                "Father", "9876500000", "Pune", LocalDate.of(2026, 11, 1),
                null, new BigDecimal("9500.00"), new BigDecimal("15000.00"), new BigDecimal("15000.00"),
                5, new BigDecimal("120.00"), LocalDate.of(2026, 11, 1), null, "Window bed", false
        );

        TenancyResponseDTO result = tenancyService.moveIn("owner@pg.in", request);
        assertThat(result.tenancyStatus()).isEqualTo(TenancyStatus.ACTIVE);
        assertThat(result.agreedMonthlyRent()).isEqualByComparingTo("9500.00");
        assertThat(bed.getBedStatus()).isEqualTo(BedStatus.OCCUPIED);
        verify(roomRepository).save(bed.getRoom());
    }

    @Test
    void moveIn_whenBedOccupied_throwsConflictException() {
        UUID propertyId = UUID.randomUUID();
        UUID bedId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).email("owner@pg.in").role(Role.PG_OWNER).active(true).build();
        PropertyEntity property = buildProperty(propertyId);
        BedEntity occupiedBed = buildBed(bedId, property, BedStatus.OCCUPIED);

        when(accessGuardService.loadActiveUser("owner@pg.in")).thenReturn(owner);
        when(bedRepository.findLockedById(bedId)).thenReturn(Optional.of(occupiedBed));
        when(accessGuardService.requirePropertyManagementAccess(owner, propertyId, "TENANTS")).thenReturn(property);

        MoveInRequestDTO request = new MoveInRequestDTO(
                bedId, null, "Karan Patel", "9876543211", null,
                null, null, null, LocalDate.of(2026, 11, 1),
                null, null, null, null, null, null, null, null, null, false
        );

        assertThatThrownBy(() -> tenancyService.moveIn("owner@pg.in", request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void moveOut_calculatesItemizedRefundAndReleasesBed() {
        UUID propertyId = UUID.randomUUID();
        UUID tenancyId = UUID.randomUUID();
        UUID bedId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).email("owner@pg.in").role(Role.PG_OWNER).active(true).build();
        PropertyEntity property = buildProperty(propertyId);
        BedEntity bed = buildBed(bedId, property, BedStatus.OCCUPIED);
        TenantEntity tenant = new TenantEntity();
        tenant.setId(UUID.randomUUID());
        tenant.setOrganization(property.getOrganization());
        tenant.setProperty(property);
        tenant.setFullName("Rohan Verma");
        tenant.setPhone("9876543210");
        tenant.setTenantStatus(TenantStatus.ACTIVE);

        TenancyEntity tenancy = new TenancyEntity();
        tenancy.setId(tenancyId);
        tenancy.setOrganization(property.getOrganization());
        tenancy.setProperty(property);
        tenancy.setTenant(tenant);
        tenancy.setRoom(bed.getRoom());
        tenancy.setBed(bed);
        tenancy.setTenancyStatus(TenancyStatus.ACTIVE);
        tenancy.setMoveInDate(LocalDate.of(2026, 10, 1));
        tenancy.setAgreedMonthlyRent(new BigDecimal("9500.00"));
        tenancy.setDepositReceivedAmount(new BigDecimal("10000.00"));

        when(accessGuardService.loadActiveUser("owner@pg.in")).thenReturn(owner);
        when(tenancyRepository.findWithDetailsById(tenancyId)).thenReturn(Optional.of(tenancy));
        when(accessGuardService.requirePropertyManagementAccess(owner, propertyId, "TENANTS")).thenReturn(property);
        when(tenancyRepository.save(any(TenancyEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bedRepository.findLockedById(bedId)).thenReturn(Optional.of(bed));
        when(tenancyRepository.existsByTenantIdAndTenancyStatusIn(any(), any())).thenReturn(false);

        MoveOutRequestDTO request = new MoveOutRequestDTO(
                LocalDate.of(2026, 11, 10),
                new BigDecimal("350.50"),
                new BigDecimal("1500.00"),
                new BigDecimal("750.00"),
                "Cupboard lock replacement",
                RefundStatus.PENDING,
                BedStatus.VACANT,
                "Keys returned"
        );

        TenancyResponseDTO result = tenancyService.moveOut("owner@pg.in", tenancyId, request);
        assertThat(result.tenancyStatus()).isEqualTo(TenancyStatus.COMPLETED);
        assertThat(result.refundableDepositAmount()).isEqualByComparingTo("7750.00");
        assertThat(result.refundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(bed.getBedStatus()).isEqualTo(BedStatus.VACANT);
        assertThat(tenant.getTenantStatus()).isEqualTo(TenantStatus.MOVED_OUT);
    }

    private PropertyEntity buildProperty(UUID propertyId) {
        OrganizationEntity organization = new OrganizationEntity();
        organization.setId(UUID.randomUUID());
        organization.setName("Upcraft Stays");
        PropertyEntity property = new PropertyEntity();
        property.setId(propertyId);
        property.setOrganization(organization);
        property.setName("Navrangpura PG");
        property.setPropertyStatus(PropertyStatus.ACTIVE);
        property.setRentDueDay(5);
        return property;
    }

    private BedEntity buildBed(UUID bedId, PropertyEntity property, BedStatus status) {
        Room room = Room.builder()
                .id(UUID.randomUUID())
                .property(property)
                .number("101")
                .floor(1)
                .sharing("2-Sharing")
                .roomType("DOUBLE")
                .capacity(2)
                .occupied(status == BedStatus.OCCUPIED ? 1 : 0)
                .price(9500.0)
                .baseMonthlyRent(new BigDecimal("9500.00"))
                .roomStatus(RoomStatus.ACTIVE)
                .build();
        BedEntity bed = new BedEntity();
        bed.setId(bedId);
        bed.setOrganization(property.getOrganization());
        bed.setProperty(property);
        bed.setRoom(room);
        bed.setBedCode("BED-1");
        bed.setMonthlyRent(new BigDecimal("9500.00"));
        bed.setDefaultDeposit(new BigDecimal("10000.00"));
        bed.setBedStatus(status);
        return bed;
    }
}

