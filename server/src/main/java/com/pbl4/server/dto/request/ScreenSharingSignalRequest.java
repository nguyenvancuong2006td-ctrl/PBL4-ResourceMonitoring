package com.pbl4.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ScreenSharingSignalRequest {

    @NotBlank(message = "Signal type không được để trống")
    @Size(max = 50, message = "Signal type không được vượt quá 50 ký tự")
    private String type;

    @NotBlank(message = "Signal data không được để trống")
    private String data;

    public ScreenSharingSignalRequest() {
    }

    public ScreenSharingSignalRequest(String type, String data) {
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}