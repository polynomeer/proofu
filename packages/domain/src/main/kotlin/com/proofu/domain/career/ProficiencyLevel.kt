package com.proofu.domain.career

/** Five-level capability scale (domain §4.3). Self-assessment and evidence-based assessment are stored separately. */
enum class ProficiencyLevel(
    val rank: Int,
) {
    NOVICE(1),
    PRACTITIONER(2),
    INDEPENDENT(3),
    ADVANCED(4),
    STRATEGIC(5),
}
