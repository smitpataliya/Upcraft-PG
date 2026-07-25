package com.upcraft.stay.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardStats {
    private int totalMembers;
    private int totalRooms;
    private int occupiedRooms;
    private int vacantRooms;
    private int pendingComplaints;
    private int openQueries;

    // Chart series for frontend Recharts components
    private List<OccupancyPoint> occupancySeries;   // last 6 months
    private List<ComplaintPoint> complaintsSeries;  // last 7 days
    private List<GrowthPoint> growthSeries;         // last 6 months

    @Data @Builder
    public static class OccupancyPoint {
        private String month;
        private int occupied;
        private int vacant;
    }

    @Data @Builder
    public static class ComplaintPoint {
        private String day;
        private int count;
    }

    @Data @Builder
    public static class GrowthPoint {
        private String month;
        private int members;
    }
}
