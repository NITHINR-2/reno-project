package com.renault.fleet.auth.controller;

import com.renault.fleet.auth.dto.SetPasswordCheckDto;
import com.renault.fleet.auth.dto.SetPasswordCheckRequest;
import com.renault.fleet.auth.dto.SetPasswordRequest;
import com.renault.fleet.auth.service.SetPasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public, rate-limited (under {@code /api/public/**}) set-password link endpoints. */
@RestController
@Tag(name = "Auth: set-password links")
public class SetPasswordController {

    private final SetPasswordService service;

    public SetPasswordController(SetPasswordService service) {
        this.service = service;
    }

    @PostMapping("/api/public/set-password/check")
    @Operation(summary = "Check a set-password link (404 unknown, 410 used/expired/replaced)")
    public ResponseEntity<SetPasswordCheckDto> check(@Valid @RequestBody SetPasswordCheckRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.check(body.token()));
    }

    @PostMapping("/api/public/set-password")
    @Operation(summary = "Set the password with a one-time link; signs the user out of every session")
    public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest body) {
        service.setPassword(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
}
