package com.example.pickdispatch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "dispatch")
public class DispatchProperties {

    /**
     * Snapshot age at or above this many minutes is treated as stale.
     * A value <= 0 disables the freshness check.
     */
    private int maxSnapshotAgeMinutes = 10;
}
