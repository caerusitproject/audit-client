package com.caerus.audit.client.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
