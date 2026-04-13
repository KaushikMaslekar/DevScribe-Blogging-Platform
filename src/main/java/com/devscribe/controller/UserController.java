package com.devscribe.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devscribe.dto.user.UpdateProfileRequest;
import com.devscribe.dto.user.UpdateUserRoleRequest;
import com.devscribe.dto.user.UserProfileResponse;
import com.devscribe.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable String username) {
        return ResponseEntity.ok(userService.getProfile(username));
    }

    @PostMapping("/{username}/follow")
    public ResponseEntity<UserProfileResponse> follow(@PathVariable String username) {
        return ResponseEntity.ok(userService.follow(username));
    }

    @DeleteMapping("/{username}/follow")
    public ResponseEntity<UserProfileResponse> unfollow(@PathVariable String username) {
        return ResponseEntity.ok(userService.unfollow(username));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(request));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserProfileResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        return ResponseEntity.ok(userService.updateUserRole(id, request.role()));
    }
}
