package com.upcraft.stay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MyRoomDto {
    private RoomDto room;
    private String bedNumber;
    private LocalDate checkInDate;
    private List<RoommateDto> roommates;
}
