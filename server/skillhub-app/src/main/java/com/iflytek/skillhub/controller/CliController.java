package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.CliWhoamiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cli")
public class CliController extends BaseApiController {

    public CliController(ApiResponseFactory responseFactory) {
        super(responseFactory);
    }

    @GetMapping("/whoami")
    public ApiResponse<CliWhoamiResponse> whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }

        return ok("response.success.read", CliWhoamiResponse.from(principal));
    }
}
