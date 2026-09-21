package com.proofu.domain.identity

import com.proofu.domain.common.UserId
import com.proofu.domain.common.domainRequire

data class ProfileLink(
    val label: String,
    val url: String,
) {
    init {
        domainRequire(label.isNotBlank()) { "profile link label must not be blank" }
        domainRequire(url.startsWith("https://") || url.startsWith("http://")) {
            "profile link url must be an absolute http(s) url"
        }
    }
}

/**
 * What a document header says about the person (name, headline, contact, links). Rendered into
 * exports only; it never enters an AI context and is not part of a document version.
 */
data class Profile(
    val userId: UserId,
    val fullName: String,
    val headline: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val links: List<ProfileLink> = emptyList(),
    val version: Long = 1,
) {
    init {
        domainRequire(fullName.isNotBlank()) { "profile name must not be blank" }
        domainRequire(fullName.length <= 120) { "profile name is too long" }
        domainRequire(email == null || EMAIL.matches(email)) { "profile email is not a valid address" }
        domainRequire(phone == null || PHONE.matches(phone)) {
            "profile phone must be digits with optional +, spaces or dashes"
        }
        domainRequire(links.size <= MAX_LINKS) { "a profile may carry at most $MAX_LINKS links" }
    }

    /** One line ATS parsers read: `email · phone · location`, only the parts that exist. */
    val contactLine: String get() = listOfNotNull(email, phone, location).joinToString(" · ")

    companion object {
        const val MAX_LINKS = 5
        private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val PHONE = Regex("\\+?[0-9][0-9 \\-]{6,18}[0-9]")
    }
}
