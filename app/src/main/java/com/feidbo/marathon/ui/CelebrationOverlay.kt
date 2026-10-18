package com.feidbo.marathon.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import com.feidbo.marathon.ui.theme.GoldDeep
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feidbo.marathon.ui.theme.Gold
import com.feidbo.marathon.ui.theme.StatusColors

@Composable
fun CelebrationOverlay(c: Celebration, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(0.6f) }
    val xpShown = remember { Animatable(0f) }
    LaunchedEffect(c) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(c) { xpShown.animateTo(c.xp.toFloat(), tween(1100, delayMillis = 250)) }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        if (c.kind != Celebration.Kind.MISSED) Confetti()
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .systemBarsPadding()
                .padding(20.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .scale(pop.value),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    when (c.kind) {
                        Celebration.Kind.DONE -> "🏆"
                        Celebration.Kind.PARTIAL -> "💪"
                        Celebration.Kind.REST -> "🧘"
                        Celebration.Kind.MISSED -> "💚"
                    },
                    fontSize = 64.sp,
                )
                Text(c.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (c.xp > 0) {
                    Text(
                        "+${xpShown.value.roundToInt()} XP",
                        fontSize = 40.sp, fontWeight = FontWeight.Black, color = GoldDeep,
                    )
                }
                if (c.streak >= 2 && c.kind != Celebration.Kind.MISSED) {
                    Pill("🔥 ${c.streak}-workout streak", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                c.prs.forEach { name ->
                    Pill("📈 New PR: $name!", StatusColors.done, Color.White)
                }
                c.levelUp?.let {
                    Text(
                        "LEVEL UP! You're now Level ${it.level}: ${it.name}",
                        style = MaterialTheme.typography.titleMedium, color = StatusColors.done,
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Black,
                    )
                }
                if (c.newBadges.isNotEmpty()) {
                    Text("New badge${if (c.newBadges.size > 1) "s" else ""}!", style = MaterialTheme.typography.titleMedium)
                    c.newBadges.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.emoji, fontSize = 32.sp)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(b.name, style = MaterialTheme.typography.titleSmall)
                                Text(b.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (c.tips.isNotEmpty()) {
                    Text("Coach's notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                    TipList(c.tips, Modifier.fillMaxWidth())
                }
                if (c.advice.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.advice.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (c.planChanges.isNotEmpty()) {
                    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("Your plan", style = MaterialTheme.typography.titleMedium)
                        c.planChanges.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (c.kind == Celebration.Kind.MISSED) "Got it" else "Let's go!")
                }
            }
        }
    }
}
