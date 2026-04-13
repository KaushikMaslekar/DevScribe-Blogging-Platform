package com.devscribe.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devscribe.dto.user.UpdateProfileRequest;
import com.devscribe.dto.user.UserProfileResponse;
import com.devscribe.entity.PostStatus;
import com.devscribe.entity.User;
import com.devscribe.entity.UserFollow;
import com.devscribe.entity.UserRole;
import com.devscribe.repository.PostRepository;
import com.devscribe.repository.UserFollowRepository;
import com.devscribe.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final UserFollowRepository userFollowRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public void deleteUser(@NonNull Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        enforceAdminOrSelf(user);

        postRepository.deleteAllByAuthorIdNative(userId);
        userRepository.deleteByIdNative(userId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName().equals(user.getEmail())) {
            SecurityContextHolder.clearContext();
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(@NonNull String username) {
        User target = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        long publishedPosts = postRepository.countByAuthor_IdAndStatus(target.getId(), PostStatus.PUBLISHED);
        long followers = userFollowRepository.countByFollowed_Id(target.getId());
        long following = userFollowRepository.countByFollower_Id(target.getId());

        User currentUser = getCurrentUserOrNull();
        boolean followedByMe = currentUser != null
                && userFollowRepository.existsByFollower_IdAndFollowed_Id(currentUser.getId(), target.getId());

        return new UserProfileResponse(
                target.getId(),
                target.getUsername(),
                target.getDisplayName(),
                target.getBio(),
                target.getAvatarUrl(),
                publishedPosts,
                followers,
                following,
                followedByMe
        );
    }

    @Transactional
    public UserProfileResponse follow(@NonNull String username) {
        User target = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
        User currentUser = getCurrentUser();

        if (currentUser.getId().equals(target.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot follow yourself");
        }

        if (!userFollowRepository.existsByFollower_IdAndFollowed_Id(currentUser.getId(), target.getId())) {
            userFollowRepository.save(UserFollow.builder().follower(currentUser).followed(target).build());
        }

        return getProfile(username);
    }

    @Transactional
    public UserProfileResponse unfollow(@NonNull String username) {
        User target = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
        User currentUser = getCurrentUser();
        userFollowRepository.deleteByFollower_IdAndFollowed_Id(currentUser.getId(), target.getId());
        return getProfile(username);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(@NonNull UpdateProfileRequest request) {
        User currentUser = getCurrentUser();

        currentUser.setDisplayName(normalizeOptionalText(request.displayName()));
        currentUser.setBio(normalizeOptionalText(request.bio()));
        currentUser.setAvatarUrl(normalizeOptionalText(request.avatarUrl()));

        User saved = userRepository.save(currentUser);
        return getProfile(saved.getUsername());
    }

    @Transactional
    public UserProfileResponse updateUserRole(@NonNull Long userId, @NonNull UserRole role) {
        enforceAdmin();
        User actor = getCurrentUser();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        user.setRole(role);
        userRepository.save(user);
        auditLogService.log(
                actor,
                "USER_ROLE_UPDATED",
                "USER",
                String.valueOf(user.getId()),
                "newRole=" + role.name()
        );
        return getProfile(user.getUsername());
    }

    private void enforceAdminOrSelf(User targetUser) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ResponseStatusException(FORBIDDEN, "Access denied");
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + UserRole.ADMIN.name()));

        boolean isSelf = authentication.getName().equals(targetUser.getEmail());

        if (!isAdmin && !isSelf) {
            throw new ResponseStatusException(FORBIDDEN, "Access denied");
        }
    }

    private void enforceAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ResponseStatusException(FORBIDDEN, "Access denied");
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + UserRole.ADMIN.name()));

        if (!isAdmin) {
            throw new ResponseStatusException(FORBIDDEN, "Admin privileges required");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(FORBIDDEN, "Authentication required");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    private User getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
