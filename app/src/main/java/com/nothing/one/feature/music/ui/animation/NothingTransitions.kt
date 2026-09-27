package com.nothing.one.feature.music.ui.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

val NothingSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
)

val NothingSpringOffset = spring<IntOffset>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.Zero,
)

val NothingOSEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(animationSpec = NothingSpring) +
        scaleIn(initialScale = 0.96f, animationSpec = NothingSpring) +
        slideInVertically(animationSpec = NothingSpringOffset) { it / 24 }
}

val NothingOSExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(animationSpec = NothingSpring) +
        scaleOut(targetScale = 0.98f, animationSpec = NothingSpring) +
        slideOutVertically(animationSpec = NothingSpringOffset) { -it / 32 }
}

val NothingOSPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(animationSpec = NothingSpring) +
        scaleIn(initialScale = 0.97f, animationSpec = NothingSpring) +
        slideInVertically(animationSpec = NothingSpringOffset) { -it / 32 }
}

val NothingOSPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(animationSpec = NothingSpring) +
        scaleOut(targetScale = 0.97f, animationSpec = NothingSpring) +
        slideOutVertically(animationSpec = NothingSpringOffset) { it / 24 }
}