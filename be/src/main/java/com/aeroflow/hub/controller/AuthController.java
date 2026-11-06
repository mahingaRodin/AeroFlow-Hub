package com.aeroflow.hub.controller;

import com.aeroflow.hub.service.CustomerRegistrationService;
import com.aeroflow.hub.service.TotpMfaService;
import com.aeroflow.hub.service.jwt.JwtService;
import com.aeroflow.hub.model.User;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomerRegistrationService customerRegistrationService;
    private final TotpMfaService totpMfaService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        if (principal instanceof User user && user.isMfaEnabled()) {
            String challengeToken = totpMfaService.createLoginChallenge(user);
            return ResponseEntity.ok(new LoginResponse(null, true, challengeToken));
        }
        return ResponseEntity.ok(new LoginResponse(jwtService.generateToken(principal), false, null));
    }

    @PostMapping("/mfa/login")
    public ResponseEntity<LoginResponse> completeMfaLogin(@Valid @RequestBody MfaLoginRequest request) {
        User user = totpMfaService.completeLoginChallenge(
                        request.challengeToken(), request.code())
                .orElseThrow(() -> new BadCredentialsException("Authentication failed"));
        return ResponseEntity.ok(new LoginResponse(jwtService.generateToken(user), false, null));
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegistrationRequest request) {
        customerRegistrationService.register(request.email(), request.password());
        return ResponseEntity.accepted().body(new MessageResponse(
                "If the account can be registered, a verification email will be sent."));
    }

    @PostMapping("/verification/resend")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        customerRegistrationService.resendVerification(request.email());
        return ResponseEntity.accepted().body(new MessageResponse(
                "If the account is awaiting verification, a verification email will be sent."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        customerRegistrationService.verifyEmail(request.token());
        return ResponseEntity.ok(new MessageResponse("Email address verified. You can now sign in."));
    }

    @PostMapping("/mfa/totp/setup")
    public ResponseEntity<TotpMfaService.TotpSetup> beginTotpEnrollment(
            Authentication authentication,
            @Valid @RequestBody PasswordRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(totpMfaService.beginTotpEnrollment(authentication.getName(), request.currentPassword()));
    }

    @PostMapping("/mfa/totp/confirm")
    public ResponseEntity<RecoveryCodesResponse> confirmTotpEnrollment(
            Authentication authentication,
            @Valid @RequestBody CodeRequest request
    ) {
        List<String> recoveryCodes = totpMfaService.confirmTotpEnrollment(authentication.getName(), request.code());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new RecoveryCodesResponse(recoveryCodes));
    }

    @PostMapping("/mfa/totp/disable")
    public ResponseEntity<Void> disableTotp(
            Authentication authentication,
            @Valid @RequestBody DisableTotpRequest request
    ) {
        totpMfaService.disableTotp(
                authentication.getName(), request.currentPassword(), request.code());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mfa/status")
    public MfaStatusResponse mfaStatus(Authentication authentication) {
        return new MfaStatusResponse(totpMfaService.isTotpEnabled(authentication.getName()));
    }

    public record LoginRequest(
            @JsonAlias("username") @NotBlank @Size(max = 254) String email,
            @NotBlank String password
    ) {
    }

    public record LoginResponse(String accessToken, boolean mfaRequired, String challengeToken) {
        public LoginResponse(String accessToken) {
            this(accessToken, false, null);
        }
    }

    public record RegistrationRequest(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password
    ) {
    }

    public record ResendVerificationRequest(@Email @NotBlank @Size(max = 254) String email) {
    }

    public record EmailVerificationRequest(@NotBlank String token) {
    }

    public record MfaLoginRequest(
            @NotBlank String challengeToken,
            @NotBlank @Size(max = 64) String code
    ) {
    }

    public record PasswordRequest(@NotBlank String currentPassword) {
    }

    public record CodeRequest(@NotBlank @Size(max = 64) String code) {
    }

    public record DisableTotpRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(max = 64) String code
    ) {
    }

    public record RecoveryCodesResponse(List<String> recoveryCodes) {
    }

    public record MfaStatusResponse(boolean totpEnabled) {
    }

    public record MessageResponse(String message) {
    }
}
