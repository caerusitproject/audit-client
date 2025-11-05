package com.caerus.audit.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerAppSettingsDto {
    @JsonProperty("configIdleTimeout")
    public Short configIdleTimeout;
    @JsonProperty("configCaptureInterval")
    public Short configCaptureInterval;
    @JsonProperty("configCommIssueAutoResolveWindow")
    public Short configCommIssueAutoResolveWindow;
    @JsonProperty("configHeartbeatInterval")
    public Short configHeartbeatInterval;
    @JsonProperty("configDestFolderPath")
    public String configDestFolderPath;
    @JsonProperty("folderStructureTemplate")
    public String folderStructureTemplate;
    @JsonProperty("tempFolderFreeSpaceThreshold")
    public Short tempFolderFreeSpaceThreshold;
    @JsonProperty("lockThreshold")
    public Short lockThreshold;
    @JsonProperty("emailNotifyEnabled")
    public Boolean emailNotifyEnabled;
}
