package biz.ugur.busroutebackend.interfaces.rest.admin.request.admin;

import biz.ugur.busroutebackend.admin.application.usecase.auth.LoginUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password
) {
        public LoginUseCase.Request toRequest() {
            return new LoginUseCase.Request(username, password);
        }
}

