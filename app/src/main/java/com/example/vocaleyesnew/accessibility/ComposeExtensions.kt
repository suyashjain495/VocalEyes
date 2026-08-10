package com.example.vocaleyesnew.accessibility

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Compose modifier extension that integrates with CentralTapHandler
 * to provide unified tap handling across Compose UI elements.
 * 
 * This modifier forwards taps to the CentralTapHandler which handles the
 * 1.5-second timeout logic to distinguish single vs double taps.
 * 
 * @param enabled Whether tap handling is enabled (default: true)
 * @param consumeGlobalTaps Whether to consume global taps (default: false)
 *                          Set to true for feature cards that should handle their own double-taps
 * @param onSingleTap Optional local single tap handler (called by CentralTapHandler after timeout)
 * @param onDoubleTap Optional local double tap handler (called by CentralTapHandler on double tap)
 */
fun Modifier.centralTapHandler(
    enabled: Boolean = true,
    consumeGlobalTaps: Boolean = false,
    onSingleTap: ((Float, Float) -> Unit)? = null,
    onDoubleTap: ((Float, Float) -> Unit)? = null
) = composed {
    if (!enabled) return@composed this
    
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = { offset ->
                if (consumeGlobalTaps) {
                    // For feature cards: Handle locally with custom tap listener
                    CentralTapHandler.registerTap(
                        offset.x, 
                        offset.y, 
                        object : TapListener {
                            override fun onSingleTap(x: Float, y: Float) {
                                // Feature cards don't handle single taps locally
                                onSingleTap?.invoke(x, y)
                            }
                            override fun onDoubleTap(x: Float, y: Float) {
                                // Feature cards handle their own double taps
                                onDoubleTap?.invoke(x, y)
                            }
                        }
                    )
                } else {
                    // For background areas: Forward to activity's tap listener
                    // The activity implements TapListener and will be called by CentralTapHandler
                    // after the 1.5-second timeout logic determines single vs double tap
                    CentralTapHandler.registerTap(
                        offset.x, 
                        offset.y, 
                        object : TapListener {
                            override fun onSingleTap(x: Float, y: Float) {
                                onSingleTap?.invoke(x, y)
                            }
                            override fun onDoubleTap(x: Float, y: Float) {
                                onDoubleTap?.invoke(x, y)
                            }
                        }
                    )
                }
            }
        )
    }
}

/**
 * Convenience modifier for feature cards that handle their own double-taps
 * and should not forward to the global tap handler.
 */
fun Modifier.featureCardTapHandler(
    onDoubleTap: () -> Unit
) = centralTapHandler(
    consumeGlobalTaps = true,
    onDoubleTap = { _, _ -> onDoubleTap() }
)

/**
 * Convenience modifier for background areas that should forward all taps
 * to the global tap handler via the activity's TapListener implementation.
 */
fun Modifier.backgroundTapHandler(
    activity: BaseAccessibleActivity
) = centralTapHandler(
    consumeGlobalTaps = false,
    onSingleTap = { x, y -> activity.onSingleTap(x, y) },
    onDoubleTap = { x, y -> activity.onDoubleTap(x, y) }
)

/**
 * Convenience modifier for background areas that should only handle single taps
 * (voice recognition) and ignore double taps.
 */
fun Modifier.singleTapOnlyBackgroundHandler(
    activity: BaseAccessibleActivity
) = centralTapHandler(
    consumeGlobalTaps = false,
    onSingleTap = { x, y -> activity.onSingleTap(x, y) },
    onDoubleTap = { _, _ -> /* Do nothing on double tap */ }
)
