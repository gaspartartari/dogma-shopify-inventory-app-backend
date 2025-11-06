package com.tartaritech.inventory_sync.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class JobStatsDTO {
    private long pending;
    private long running;
    private long done;
    private long failed;
    private long dead;
    private long total;
}
