package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.identity.dto.ChangePasswordRequest
import com.kenlikdev.qmarket.identity.dto.ProfileResponse
import com.kenlikdev.qmarket.identity.dto.UpdateProfileRequest
import com.kenlikdev.qmarket.identity.service.ProfileService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class ProfileController(
    private val profileService: ProfileService,
) {
    @GetMapping("/me")
    fun me(authentication: Authentication): ProfileResponse = profileService.getMyProfile(currentUserId(authentication))

    @PatchMapping("/me")
    fun updateMe(
        authentication: Authentication,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ProfileResponse = profileService.updateMyProfile(currentUserId(authentication), request)

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        authentication: Authentication,
        @Valid @RequestBody request: ChangePasswordRequest,
    ) {
        profileService.changePassword(currentUserId(authentication), request)
    }

    private fun currentUserId(authentication: Authentication): UUID = authentication.principal as UUID
}
