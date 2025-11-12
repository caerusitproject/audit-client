package com.caerus.audit.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventLogRequest {
    private Byte eventTypeId;
    private String eventDesc;
    private String eventSource;
    private String eventSrcIPAddr;
    private Instant eventDTime;
}
