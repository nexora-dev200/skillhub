package com.iflytek.skillhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ApiResponseFactory apiResponseFactory;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper, ApiResponseFactory apiResponseFactory) {
        this.objectMapper = objectMapper;
        this.apiResponseFactory = apiResponseFactory;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ApiResponse<Void> body = apiResponseFactory.error(403, "error.forbidden");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
