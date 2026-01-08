package com.caerus.audit.client.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorLogRequest {
  private Byte errorTypeId;
  private String errorDesc;
  private String errorSource;
  private String errorSrcIPAddr;
  private Instant errorDTime;
}
