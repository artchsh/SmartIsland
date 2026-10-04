/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.model.IslandMode

/**
 * Single source of truth for how each island mode presents itself.
 *
 * Before this existed, the mode-to-presentation mapping was duplicated across
 * seven `when` blocks in four files:
 *
 *   - `IslandCollapsedContent` x2  (compact leading slot, compact trailing slot)
 *   - `IslandOverlayView` x2        (companion bubble, height estimate)
 *   - `IslandExpandedContent` x3    (expanded card, backdrop accent, height clamp)
 *
 * Three of those ended in `IslandMode.Empty -> Unit`, which silently swallowed a
 * newly added mode instead of failing to compile, so adding a mode meant finding
 * all seven and getting each right. AUDIT.md section 5.10.
 *
 * The colour duplication was actively harmful: the collapsed Navigation slot read
 * `settings.liveActivityColor` while the expanded layer read
 * `settings.navigationColor`, so changing the Navigation colour did nothing to the
 * pill. AUDIT.md section 5.12. With one resolver that class of bug cannot recur.
 *
 * ## Height
 *
 * The HIG gives a single height range for the expanded presentation: 84-160pt,
 * regardless of activity type. Dot Island had per-mode ranges that both
 * contradicted the HIG (LiveActivity was allowed up to 205dp) and contradicted
 * each other (`defaultEstimatedHeightForMode` estimated LiveActivity at 180dp
 * while `clampHeightForMode` permitted 140-205dp, so the estimate was never a
 * useful fallback). The shared range remains the baseline. Music intentionally
 * permits 180dp to retain equal 20dp insets, 64dp artwork and 48dp touch targets
 * on Android; see docs/ISLAND_STYLE.md. Estimates precede real measurement.
 */
@Immutable
data class IslandModePresentation(
    val mode: IslandMode,
    /** Starting height before the card's content has been measured. */
    val estimatedHeightDp: Float,
    /** Accent colour used for glyphs, progress and tinting. */
    val accent: (DotIslandSettings) -> Color,
    /**
     * Whether the compact presentation may show a companion circle when several
     * activities are active. Mirrors the Dynamic Island split behaviour.
     */
    val supportsCompanion: Boolean = true
) {
    val estimatedHeight: Dp get() = estimatedHeightDp.dp
    val minHeight: Dp get() = MIN_EXPANDED_HEIGHT_DP.dp
    val maxHeight: Dp get() = if (mode == IslandMode.Music) MUSIC_EXPANDED_HEIGHT_DP.dp else MAX_EXPANDED_HEIGHT_DP.dp

    /** Clamps measured height to this mode's documented presentation budget. */
    fun clampHeight(height: Dp): Dp = height.coerceIn(minHeight, maxHeight)
}

/**
 * Baseline expanded-presentation height range; music has a documented 180dp exception.
 *
 * See REDESIGN.md section 2. Page content becomes vertically scrollable above
 * the ceiling so that clamping cannot make overflow unreachable.
 */
const val MIN_EXPANDED_HEIGHT_DP = 84f
const val MAX_EXPANDED_HEIGHT_DP = 160f

/** Music's equal 20dp outer content insets plus full-size artwork and touch targets. */
const val MUSIC_EXPANDED_HEIGHT_DP = 180f

/**
 * Hairline border around the island, matching the Dynamic Island.
 *
 * Sampled from a reference screenshot of the collapsed pill: border pixels read
 * RGB(40,40,40) against a pure black (0,0,0) interior, i.e. neutral grey at
 * 40/255 = 0.157 alpha of white, and 2-3px wide on a 1179px-wide capture, which
 * works out to roughly 1dp.
 *
 * Defined as white-with-alpha rather than a literal grey so it stays neutral over
 * both the black interior and any album artwork that ends up behind it.
 *
 * (A second reference shot of the expanded card sampled RGB(22,22,22); the two
 * images were separate photographs and the difference is exposure, not intent.)
 */
val ISLAND_BORDER_COLOR: Color = Color.White.copy(alpha = 40f / 255f)
val ISLAND_BORDER_WIDTH: Dp = 1.dp

/**
 * Registry for every [IslandMode].
 *
 * `IslandMode.Empty` has no entry: it means "nothing to show", and callers use
 * [presentationFor]'s fallback for it rather than rendering an empty card.
 */
private val REGISTRY: Map<IslandMode, IslandModePresentation> = buildMap {
    fun put(
        mode: IslandMode,
        estimatedHeightDp: Float,
        accent: (DotIslandSettings) -> Color
    ) {
        put(mode, IslandModePresentation(mode, estimatedHeightDp, accent))
    }

    // Estimate heights are biased toward the upper half of the HIG range for
    // content-dense modes so the card does not visibly grow on first paint.
    put(IslandMode.Music, MUSIC_EXPANDED_HEIGHT_DP) { Color(it.musicVisualizerColor) }
    put(IslandMode.IncomingCall, 118f) { Color(it.callColor) }
    put(IslandMode.LiveActivity, 155f) { Color(it.liveActivityColor) }
    put(IslandMode.Published, 148f) { Color.White }
}

/**
 * Presentation for [mode], falling back to the generic notification presentation
 * for `Empty` and `null` so callers never have to null-check.
 */
fun presentationFor(mode: IslandMode?): IslandModePresentation =
    REGISTRY[mode] ?: REGISTRY.getValue(IslandMode.LiveActivity)

/** Accent colour for [mode]. The single resolver for every mode's colour. */
fun accentColorFor(mode: IslandMode?, settings: DotIslandSettings): Color =
    presentationFor(mode).accent(settings)
