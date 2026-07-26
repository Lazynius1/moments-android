package com.moments.android.views.login

import com.moments.android.models.InterestOption

/**
 * Lista onboarding: mismos keys Firestore que iOS (`InterestModels`).
 * El modelo vive en `models/InterestModels.kt`.
 */
val AllInterests: List<InterestOption> = InterestOption.allKnown

const val INTERESTS_MIN = 3
const val INTERESTS_MAX = 5
