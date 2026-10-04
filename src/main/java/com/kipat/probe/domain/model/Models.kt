package com.kipat.probe.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class VerificationLevel {
    SECURE_CARD_LIVE,
    SECURE_CARD_CACHED,
    STANDARD_CARD,
    PHONE_SHARE_VERIFIED,
    ACCOUNT_ONLY,
    UNVERIFIED,
    REVOKED
}

@Serializable
data class VerificationInfo(
    val level: VerificationLevel,
    val label: String
)

@Serializable
data class ProfileData(
    val displayName: String,
    val subtitle: String? = null,
    val organization: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null
)

@Serializable
data class Action(
    val type: String,
    val label: String,
    val url: String? = null,
    val enabled: Boolean = true
)

@Serializable
data class ExperienceManifest(
    val schema: Int = 1,
    val experienceId: String,
    val subjectUserId: String,
    val verification: VerificationInfo,
    val profile: ProfileData,
    val profileTemplateSlug: String,
    val animationTemplateSlug: String,
    val themeSlug: String,
    val actions: List<Action> = emptyList()
)

@Serializable
data class Identity(
    val id: String,
    val name: String,
    val type: String,
    val profileTemplateId: String,
    val animationTemplateId: String,
    val themeId: String,
    val isDefault: Boolean = false
)
