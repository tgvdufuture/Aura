package com.aura.led.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/**
 * The owner's mascot, on the stage it needs to be seen.
 *
 * The artwork is pale lavender with a dark face, which is exactly the colour of the light theme's
 * own surfaces (`secondaryContainer` and friends): pasted straight onto them the creature is a
 * ghost — its antennae were the first thing to disappear, seen on the device. It therefore carries
 * a contour in its own ink colour: a shadow on a light surface, invisible on a dark one, so one
 * component works on every surface without the callers having to know which theme is on.
 *
 * The mascot is decorative by construction — whatever its face says, the copy next to it says in
 * words — so it is removed from the accessibility tree instead of being announced twice.
 */
@Composable
fun AuraMascot(
    mood: MascotMood,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    // "Remove animations" in Android's accessibility settings means exactly what it says: the
    // mascot still appears, it just stops moving.
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }

    // One authored moment: the mascot settles in when it appears and when the state changes, which
    // is a real event. A repeated entrance on every recomposition is what makes an interface feel
    // assembled; one deliberate shift reads as alive.
    val appear = remember { Animatable(if (animatorsEnabled) 0f else 1f) }
    LaunchedEffect(mood, animatorsEnabled) {
        if (!animatorsEnabled) {
            appear.snapTo(1f)
            return@LaunchedEffect
        }
        appear.snapTo(0f)
        appear.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier.size(size).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val art = painterResource(mood.art)
        // The contour: the mascot's own alpha, grown a few percent and filled with its ink colour.
        // It follows every antenna and corner exactly because it is the artwork, not a shape drawn
        // around it, and it is what keeps the thin pale parts readable on a pale surface.
        Image(
            painter = art,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(STAGE_INK, BlendMode.SrcIn),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appear.value
                    scaleX = CONTOUR_SCALE
                    scaleY = CONTOUR_SCALE
                },
        )
        Image(
            painter = art,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appear.value
                    val scale = 0.92f + 0.08f * appear.value
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/**
 * The ink the mascot's own face is drawn in (`#241436`, sampled from `joy.png` and `oh.png`) at
 * partial opacity. Deliberately not a theme colour: the artwork is a fixed illustration, its
 * palette does not follow the app's.
 */
private val STAGE_INK = Color(0xFF241436).copy(alpha = 0.45f)

/** How far the contour grows past the artwork. Enough to be a rim, not enough to be a border. */
private const val CONTOUR_SCALE = 1.05f
