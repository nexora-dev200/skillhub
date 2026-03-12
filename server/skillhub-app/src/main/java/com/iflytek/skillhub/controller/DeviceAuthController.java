package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.device.DeviceCodeResponse;
import com.iflytek.skillhub.auth.device.DeviceTokenResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cli/auth/device")
public class DeviceAuthController {

    private final DeviceAuthService deviceAuthService;

    public DeviceAuthController(DeviceAuthService deviceAuthService) {
        this.deviceAuthService = deviceAuthService;
    }

    @PostMapping("/code")
    public DeviceCodeResponse requestDeviceCode() {
        return deviceAuthService.generateDeviceCode();
    }

    @PostMapping("/token")
    public DeviceTokenResponse pollToken(@RequestBody TokenRequest request) {
        return deviceAuthService.pollToken(request.deviceCode());
    }

    public record TokenRequest(String deviceCode) {}
}
