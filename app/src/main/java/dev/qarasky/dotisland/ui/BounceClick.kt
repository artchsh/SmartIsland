/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * A clickable that scales down slightly while pressed.
 *
 * This previously delegated to a bare
 * `pointerInput { detectTapGestures { ... } }` inside `Modifier.composed`.
 * That has two problems, both of which this version fixes:
 *
 *  1. **It removed every control from the accessibility tree.** A raw
 *     `pointerInput` emits no `SemanticsModifier`, so the node had no click
 *     action, no `Role`, and no bounds for assistive tech. TalkBack, Switch
 *     Access and Accessibility Scanner could not see or invoke any of them.
 *     This affected all 50 `bounceClick` call sites: media transport, timer
 *     pause/stop, stopwatch lap/reset, notification action chips, call
 *     answer/decline, hotspot toggle, and the primary controls on the settings
 *     screens.
 *
 *  2. **It used the deprecated `Modifier.composed`.** That API has been
 *     discouraged since Compose 1.2 because it defeats modifier skipping and
 *     blocks modifier-node reuse.
 *
 * Both are fixed by building on `Modifier.clickable`, which contributes proper
 * semantics and role information, while the press animation is driven from an
 * `InteractionSource` so it costs nothing extra.
 *
 * @param enabled when false the node is not clickable and is reported as
 *   disabled to assistive tech.
 * @param onClickLabel announced by TalkBack in place of the generic "double tap
 *   to activate". Prefer passing a localised description.
 * @param role the semantic role, e.g. [Role.Button] or [Role.Switch].
 */
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) BOUNCE_PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
}

private const val BOUNCE_PRESSED_SCALE = 0.90f
