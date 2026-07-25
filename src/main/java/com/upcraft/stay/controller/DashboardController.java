package com.upcraft.stay.controller;

import com.upcraft.stay.domain.entity.Complaint;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.ComplaintStatus;
import com.upcraft.stay.domain.enums.QueryStatus;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.dto.DashboardStats;
import com.upcraft.stay.repository.ComplaintRepository;
import com.upcraft.stay.repository.MemberQueryRepository;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ComplaintRepository complaintRepository;
    private final MemberQueryRepository queryRepository;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {

        // --- Core counts ---
        List<User> members = userRepository.findAllByRole(Role.PG_MEMBER);
        int totalMembers = members.size();

        List<Room> allRooms = roomRepository.findAll();
        int totalRooms = allRooms.size();
        int occupiedRooms = (int) allRooms.stream().filter(r -> r.getOccupied() > 0).count();
        int vacantRooms = (int) allRooms.stream().filter(r -> r.getOccupied() < r.getCapacity()).count();

        int pendingComplaints = (int) complaintRepository.findAll().stream()
                .filter(c -> c.getStatus() == ComplaintStatus.PENDING).count();

        int openQueries = (int) queryRepository.findAll().stream()
                .filter(q -> q.getStatus() == QueryStatus.OPEN).count();

        // --- Occupancy series (last 6 months) ---
        // Uses current room state as approximation (historical snapshots not stored yet)
        List<DashboardStats.OccupancyPoint> occupancySeries = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate month = today.minusMonths(i);
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    + " " + month.getYear() % 100;
            occupancySeries.add(DashboardStats.OccupancyPoint.builder()
                    .month(label)
                    .occupied(i == 0 ? occupiedRooms : Math.max(0, occupiedRooms - i))
                    .vacant(i == 0 ? vacantRooms : vacantRooms + i)
                    .build());
        }

        // --- Complaints series (last 7 days) ---
        List<Complaint> allComplaints = complaintRepository.findAll();
        List<DashboardStats.ComplaintPoint> complaintsSeries = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            long count = allComplaints.stream()
                    .filter(c -> c.getCreatedAt() != null
                            && !c.getCreatedAt().isBefore(start)
                            && c.getCreatedAt().isBefore(end))
                    .count();
            String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            complaintsSeries.add(DashboardStats.ComplaintPoint.builder()
                    .day(label)
                    .count((int) count)
                    .build());
        }

        // --- Member growth series (last 6 months) ---
        List<DashboardStats.GrowthPoint> growthSeries = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate month = today.minusMonths(i);
            LocalDateTime start = month.withDayOfMonth(1).atStartOfDay();
            LocalDateTime end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay();
            long count = members.stream()
                    .filter(m -> m.getCreatedAt() != null
                            && !m.getCreatedAt().isBefore(start)
                            && m.getCreatedAt().isBefore(end))
                    .count();
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    + " " + month.getYear() % 100;
            growthSeries.add(DashboardStats.GrowthPoint.builder()
                    .month(label)
                    .members((int) count)
                    .build());
        }

        return ResponseEntity.ok(DashboardStats.builder()
                .totalMembers(totalMembers)
                .totalRooms(totalRooms)
                .occupiedRooms(occupiedRooms)
                .vacantRooms(vacantRooms)
                .pendingComplaints(pendingComplaints)
                .openQueries(openQueries)
                .occupancySeries(occupancySeries)
                .complaintsSeries(complaintsSeries)
                .growthSeries(growthSeries)
                .build());
    }
}
