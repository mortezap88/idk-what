package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryPurpleLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ReviewActivityPhase {
    IDLE,
    LISTENING,
    THINKING
}

/**
 * Minimalistic yet mathematically intricate phase-morphing indicator for the Review Answer page.
 *
 * - **Listening**: Renders three coupled Fourier-harmonic standing-wave rings modulated by live
 *   microphone voice energy (`audioLevel`) with subtle orbital satellites.
 * - **Thinking**: Smoothly morphs (`morph` 0 -> 1) into three 3D-projected precessing gyroscopic
 *   Lissajous / Torus-knot orbits with phase-locked traveling nodes.
 */
@Composable
fun ReviewActivityIndicator(
    phase: ReviewActivityPhase,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val isVisible = phase != ReviewActivityPhase.IDLE
    // Remember the last active phase during exit fade so the label & geometry don't snap to empty
    val displayPhase = if (phase != ReviewActivityPhase.IDLE) phase else ReviewActivityPhase.THINKING

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.94f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(240, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        val morph by animateFloatAsState(
            targetValue = if (displayPhase == ReviewActivityPhase.THINKING) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessLow
            ),
            label = "phaseMorph"
        )

        val smoothedAudio by animateFloatAsState(
            targetValue = if (displayPhase == ReviewActivityPhase.LISTENING) {
                audioLevel.coerceIn(0f, 1f)
            } else {
                0f
            },
            animationSpec = tween(durationMillis = 85, easing = FastOutSlowInEasing),
            label = "smoothedVoiceLevel"
        )

        val infinite = rememberInfiniteTransition(label = "harmonicPhaseTransition")
        val primaryTau by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "primaryTau"
        )
        val precessionTau by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "precessionTau"
        )
        val pulse by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "subtlePulse"
        )

        val labelText = when (displayPhase) {
            ReviewActivityPhase.LISTENING -> "Listening"
            ReviewActivityPhase.THINKING -> "Thinking"
            ReviewActivityPhase.IDLE -> ""
        }

        val primaryStrokeColor = TextPrimary
        val secondaryStrokeColor = TextSecondary
        val mutedStrokeColor = TextMuted
        val highlightColor = PrimaryPurpleLight

        val wavePaths = remember { Array(3) { Path() } }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = SurfaceCard.copy(alpha = 0.90f),
            border = BorderStroke(
                width = 1.dp,
                color = SurfaceCardBorder.copy(alpha = 0.85f)
            ),
            shadowElevation = 4.dp,
            modifier = Modifier
                .testTag("review_activity_indicator")
                .semantics { contentDescription = labelText }
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(32.dp)) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val minDim = minOf(size.width, size.height)
                        val baseRadius = minDim * 0.36f
                        val voiceBoost = smoothedAudio * 0.85f + 0.15f * pulse

                        // Outer ultra-fine reference horizon ring
                        drawCircle(
                            color = mutedStrokeColor.copy(alpha = 0.18f + 0.08f * pulse),
                            radius = minDim * 0.45f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 0.75.dp.toPx())
                        )

                        val sampleSteps = 84
                        val twoPi = (2.0 * PI).toFloat()

                        for (layer in 0 until 3) {
                            val path = wavePaths[layer]
                            path.reset()

                            val layerOffset = layer * (twoPi / 3f)
                            val direction = if (layer % 2 == 0) 1f else -1f

                            // Listening geometry: concentric harmonic standing-wave rings
                            val listenBaseR = baseRadius * (0.64f + layer * 0.17f) *
                                (1f + smoothedAudio * 0.12f)
                            val harmonicFreqA = 3 + layer * 2   // 3, 5, 7 lobes
                            val harmonicFreqB = 5 + layer * 3   // 5, 8, 11 secondary harmonics
                            val waveAmp = baseRadius * (0.07f + voiceBoost * (0.20f - layer * 0.03f))

                            // Thinking geometry: 3D-projected precessing gyroscopic torus-knot / Lissajous orbits
                            val tiltAngle = layerOffset + precessionTau * 0.65f * direction
                            val inclination = 0.52f + 0.36f * sin(precessionTau + layer * 2.1f)
                            val cosTilt = cos(tiltAngle)
                            val sinTilt = sin(tiltAngle)
                            val orbitR = baseRadius * (1.02f - layer * 0.06f)

                            for (i in 0..sampleSteps) {
                                val u = (i.toFloat() / sampleSteps.toFloat()) * twoPi

                                // 1) Acoustic harmonic radial coordinate (Listening)
                                val standingA = sin(u * harmonicFreqA + primaryTau * 2.0f * direction + layerOffset)
                                val standingB = cos(u * harmonicFreqB - precessionTau * 3.0f + layerOffset)
                                val rListen = listenBaseR + waveAmp * (0.68f * standingA + 0.32f * standingB)
                                val xListen = cx + rListen * cos(u)
                                val yListen = cy + rListen * sin(u)

                                // 2) Coupled 3D gyroscopic knot coordinate (Thinking)
                                val knotRipple = 1f + 0.14f * sin(3f * u + primaryTau * 2f * direction + layerOffset)
                                val ox = orbitR * knotRipple * cos(u)
                                val oy = orbitR * knotRipple * sin(u) * inclination
                                val xThink = cx + (ox * cosTilt - oy * sinTilt)
                                val yThink = cy + (ox * sinTilt + oy * cosTilt)

                                // Smooth continuous interpolation between Listening & Thinking topologies
                                val px = xListen + (xThink - xListen) * morph
                                val py = yListen + (yThink - yListen) * morph

                                if (i == 0) {
                                    path.moveTo(px, py)
                                } else {
                                    path.lineTo(px, py)
                                }
                            }
                            path.close()

                            val strokeColor = when (layer) {
                                0 -> primaryStrokeColor.copy(alpha = 0.86f)
                                1 -> secondaryStrokeColor.copy(alpha = 0.62f)
                                else -> mutedStrokeColor.copy(alpha = 0.46f)
                            }
                            val strokePx = when (layer) {
                                0 -> 1.25.dp.toPx()
                                1 -> 1.0.dp.toPx()
                                else -> 0.85.dp.toPx()
                            }

                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = strokePx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Phase-locked orbital node gliding along each harmonic/gyroscopic curve
                            val nodeU = (primaryTau * (1.3f + layer * 0.35f) * direction + layerOffset)
                            val nStandA = sin(nodeU * harmonicFreqA + primaryTau * 2.0f * direction + layerOffset)
                            val nStandB = cos(nodeU * harmonicFreqB - precessionTau * 3.0f + layerOffset)
                            val nrListen = listenBaseR + waveAmp * (0.68f * nStandA + 0.32f * nStandB)
                            val nxListen = cx + nrListen * cos(nodeU)
                            val nyListen = cy + nrListen * sin(nodeU)

                            val nKnot = 1f + 0.14f * sin(3f * nodeU + primaryTau * 2f * direction + layerOffset)
                            val nox = orbitR * nKnot * cos(nodeU)
                            val noy = orbitR * nKnot * sin(nodeU) * inclination
                            val nxThink = cx + (nox * cosTilt - noy * sinTilt)
                            val nyThink = cy + (nox * sinTilt + noy * cosTilt)

                            val nodeX = nxListen + (nxThink - nxListen) * morph
                            val nodeY = nyListen + (nyThink - nyListen) * morph

                            drawCircle(
                                color = highlightColor.copy(alpha = if (layer == 0) 0.95f else 0.72f),
                                radius = (if (layer == 0) 1.9.dp else 1.4.dp).toPx(),
                                center = Offset(nodeX, nodeY)
                            )
                        }

                        // Subtle breathing focal core
                        val coreRadius = (1.6f + 0.9f * voiceBoost + 0.5f * morph * pulse).dp.toPx()
                        drawCircle(
                            color = highlightColor.copy(alpha = 0.22f + 0.18f * pulse),
                            radius = coreRadius * 1.9f,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = highlightColor.copy(alpha = 0.92f),
                            radius = coreRadius,
                            center = Offset(cx, cy)
                        )
                    }
                }

                AnimatedContent(
                    targetState = labelText,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(190, easing = FastOutSlowInEasing))
                            togetherWith
                            fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "reviewActivityLabel"
                ) { text ->
                    Text(
                        text = text,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        modifier = Modifier.testTag("review_activity_label")
                    )
                }
            }
        }
    }
}
