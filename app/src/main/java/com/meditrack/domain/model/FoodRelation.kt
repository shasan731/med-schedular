package com.meditrack.domain.model

/**
 * When a dose should be taken relative to food, as a doctor might advise
 * ("before food", "after food", "with food"). [NONE] means no specific advice.
 */
enum class FoodRelation {
    NONE,
    BEFORE_FOOD,
    AFTER_FOOD,
    WITH_FOOD
}
