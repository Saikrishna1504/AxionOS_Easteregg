package com.axion.os.easteregg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MainContainer()
            }
        }
    }
}

val ExpoOut = Easing { x -> if (x == 1f) 1f else 1f - 2.0.pow(-10.0 * x.toDouble()).toFloat() }
val BackEaseIn = Easing { x -> val s = 1.70158f; x * x * ((s + 1) * x - s) }

data class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val alpha: Float)
data class Bullet(var position: Offset, val color: Color = Color.White, val damage: Int = 150, val isEnemy: Boolean = false)
data class Particle(val pos: Offset, val vel: Offset, val color: Color, var life: Float, val size: Float = 4f)
data class CombatLog(val message: String, val color: Color)

data class GameStat(val timestamp: Long, val result: String, val time: String, val accuracy: Int)

class StatsManager(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("axion_stats_final_v16", android.content.Context.MODE_PRIVATE)
    var history by mutableStateOf(getHistoryFromPrefs())
        private set

    fun save(stat: GameStat) {
        val current = getHistoryFromPrefs().toMutableList()
        current.add(0, stat)
        val limited = current.take(10)
        val array = JSONArray()
        limited.forEach { 
            val obj = JSONObject(); obj.put("ts", it.timestamp); obj.put("res", it.result); obj.put("time", it.time); obj.put("acc", it.accuracy)
            array.put(obj)
        }
        prefs.edit().putString("history", array.toString()).apply()
        history = limited
    }

    private fun getHistoryFromPrefs(): List<GameStat> {
        val json = prefs.getString("history", null) ?: return emptyList()
        val list = mutableListOf<GameStat>(); val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(GameStat(obj.getLong("ts"), obj.getString("res"), obj.getString("time"), obj.optInt("acc", 0)))
        }
        return list
    }
}

@Composable
fun MainContainer() {
    var gameState by remember { mutableStateOf("SPLASH") }
    val context = LocalContext.current
    val statsManager = remember { StatsManager(context) }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        val stars = remember { List(100) { Star(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 1.5f + 0.5f, Random.nextFloat() * 0.1f + 0.05f, Random.nextFloat() * 0.4f + 0.1f) } }
        val starOffset by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing)))

        Canvas(modifier = Modifier.fillMaxSize()) {
            stars.forEach { star ->
                val currentY = (star.y + starOffset * star.speed * 20f) % 1f
                drawCircle(Color.White.copy(alpha = star.alpha), radius = star.size, center = Offset(star.x * size.width, currentY * size.height))
            }
        }

        Crossfade(targetState = gameState, animationSpec = tween(1200)) { state ->
            when(state) {
                "SPLASH" -> SplashScreen(onFinish = { gameState = "ANIMATION" })
                "ANIMATION" -> AtomCrashGame(onFinish = { gameState = "PREP" })
                "PREP" -> CombatPrepScreen(onStart = { gameState = "GAME" })
                "GAME" -> SpaceGame(statsManager, onExit = { gameState = "SUMMARY" })
                "SUMMARY" -> SummaryScreen(statsManager, onExit = { gameState = "SPLASH" })
            }
        }
    }
}

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) { delay(3000); onFinish() }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Axion", style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.ExtraLight, color = Color.White, letterSpacing = 2.sp))
                Text("OS", style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp, drawStyle = Stroke(width = 2f)))
            }
            Spacer(Modifier.height(16.dp))
            Text("Make your android faster.\nmore powerful.\nmore reliable.\naxion.", style = TextStyle(fontSize = 12.sp, color = Color.White.copy(0.4f), textAlign = TextAlign.Center, letterSpacing = 2.sp), lineHeight = 20.sp)
        }
    }
}

@Composable
fun CombatPrepScreen(onStart: () -> Unit) {
    var visibleChars by remember { mutableIntStateOf(0) }
    val fullText = "SYNCING FLIGHT COURSE..."
    LaunchedEffect(Unit) { while (visibleChars < fullText.length) { delay(100); visibleChars++ } }

    Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onStart() }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (visibleChars < fullText.length) fullText.take(visibleChars) else "-> AXION COURSE ENGAGED",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp, color = Color.White),
                textAlign = TextAlign.Center
            )

            if (visibleChars >= fullText.length) {
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        drawCircle(Color.White, radius = 90.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                        drawCircle(Color.Black, radius = 88.dp.toPx(), center = center)
                        drawCircle(Color.White.copy(0.7f), radius = 35.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                        drawArc(Color.White.copy(0.2f), 180f, 120f, false, center - Offset(105.dp.toPx(), 105.dp.toPx()), Size(210.dp.toPx(), 210.dp.toPx()), style = Stroke(4.dp.toPx()))
                        drawArc(Color.White.copy(0.2f), 0f, 120f, false, center - Offset(105.dp.toPx(), 105.dp.toPx()), Size(210.dp.toPx(), 210.dp.toPx()), style = Stroke(4.dp.toPx()))
                        val gRect = Rect(center.x - 45.dp.toPx(), center.y - 45.dp.toPx(), center.x + 45.dp.toPx(), center.y + 45.dp.toPx())
                        drawArc(Color.White, 35f, 290f, false, gRect.topLeft, gRect.size, style = Stroke(7.dp.toPx()))
                        drawLine(Color.White, center, Offset(center.x + 45.dp.toPx(), center.y), strokeWidth = 7.dp.toPx())
                    }
                }
                Spacer(Modifier.height(40.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DamageKeyRow(Color.White.copy(0.2f), "0 DAMAGE (SHIELD)")
                    DamageKeyRow(Color.White, "150 DAMAGE (CORE)")
                }
                Spacer(Modifier.weight(1f))
                Text("TAP TO INITIATE", style = TextStyle(fontSize = 12.sp, color = Color.White.copy(0.4f), letterSpacing = 6.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun SpaceGame(statsManager: StatsManager, onExit: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var gameTimeByFrame by remember { mutableLongStateOf(0L) }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sw = constraints.maxWidth.toFloat(); val sh = constraints.maxHeight.toFloat()
        var shipPos by remember { mutableStateOf(Offset(sw / 2, sh * 0.85f)) }
        var bossPos by remember { mutableStateOf(Offset(sw / 2, -200f)) }
        var bossHealth by remember { mutableStateOf(2500f) }
        var gameStatus by remember { mutableStateOf("PLAYING") }
        
        val bullets = remember { mutableStateListOf<Bullet>() }
        val particles = remember { mutableStateListOf<Particle>() }
        val logs = remember { mutableStateListOf<CombatLog>() }
        var firedCount by remember { mutableIntStateOf(0) }; var hitCount by remember { mutableIntStateOf(0) }
        var gameTime by remember { mutableLongStateOf(0L) }
        val animH by animateFloatAsState(bossHealth / 2500f, spring(Spring.DampingRatioNoBouncy), label = "h")
        val breakdownProgress = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            val coreRadiusPx = with(density) { 110.dp.toPx() }
            val shieldRadiusPx = with(density) { 150.dp.toPx() }
            
            var start = -1L; var lastShot = 0L
            while(gameStatus == "PLAYING") {
                withFrameMillis { now ->
                    if (start == -1L) start = now
                    gameTime = now - start; gameTimeByFrame = gameTime
                    val xVal = (sw/2) + sin(gameTime/700f)*(sw*0.4f)
                    val yVal = sh*0.12f + (1f + sin(gameTime/1200f))*sh*0.22f
                    bossPos = Offset(xVal, yVal)
                    
                    val shotInterval = if (bossHealth < 500) 800L else if (bossHealth < 1500) 1100L else 1500L
                    if (gameTime - lastShot > shotInterval) {
                        if (bossHealth < 500) { 
                            bullets.add(Bullet(bossPos + Offset(-40f, 80f), Color.White, 0, true))
                            bullets.add(Bullet(bossPos + Offset(0f, 100f), Color.White, 0, true))
                            bullets.add(Bullet(bossPos + Offset(40f, 80f), Color.White, 0, true))
                        } else if (bossHealth < 1500) { 
                            bullets.add(Bullet(bossPos + Offset(-30f, 80f), Color.White, 0, true))
                            bullets.add(Bullet(bossPos + Offset(30f, 80f), Color.White, 0, true))
                        } else { bullets.add(Bullet(bossPos + Offset(0f, 100f), Color.White, 0, true)) }
                        lastShot = gameTime
                    }

                    val bIt = bullets.listIterator()
                    while(bIt.hasNext()) {
                        val b = bIt.next()
                        if (b.isEnemy) {
                            b.position = Offset(b.position.x, b.position.y + 14f)
                            if ((b.position - shipPos).getDistance() < 50f) {
                                gameStatus = "BREAKDOWN"
                                scope.launch {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    repeat(20) { particles.add(Particle(shipPos, Offset(Random.nextFloat()*40-20, Random.nextFloat()*40-20), Color.White, 1.5f, 6f)) }
                                    breakdownProgress.animateTo(1f, tween(1000))
                                    gameStatus = "LOST"
                                }
                            }
                        } else {
                            b.position = Offset(b.position.x, b.position.y - 45f)
                            val dist = (b.position - bossPos).getDistance()
                            
                            if (dist < shieldRadiusPx) { 
                                val isCoreHit = dist < coreRadiusPx
                                val finalDamage = if (isCoreHit) 150 else 0
                                bossHealth -= finalDamage.toFloat()
                                if (finalDamage > 0) hitCount++
                                bIt.remove()
                                
                                val logMsg = if (finalDamage > 0) "HIT >> [DEALT 150 DMG]" else "SHIELDED >> [0 DMG]"
                                logs.add(0, CombatLog(logMsg, Color.White)); if (logs.size > 4) logs.removeLast()
                                haptic.performHapticFeedback(if (finalDamage > 0) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
                                repeat(6) { particles.add(Particle(b.position, Offset(Random.nextFloat()*20-10, Random.nextFloat()*20-10), Color.White, 1f)) }
                                if (bossHealth <= 0) { bossHealth = 0f; gameStatus = "WON" }
                                continue
                            }
                        }
                        if (b.position.y < -150f || b.position.y > sh + 150f) bIt.remove()
                    }
                    val pIt = particles.listIterator()
                    while(pIt.hasNext()) { val p = pIt.next(); p.life -= 0.05f; if (p.life <= 0) pIt.remove() }
                }
            }
            if (gameStatus != "PLAYING" && gameStatus != "BREAKDOWN") {
                val acc = if (firedCount > 0) (hitCount * 100 / firedCount) else 0
                val centis = (gameTime % 1000) / 10; val secs = (gameTime / 1000) % 60; val mins = (gameTime / 60000)
                val timeStr = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}:${centis.toString().padStart(2, '0')}"
                statsManager.save(GameStat(System.currentTimeMillis(), gameStatus, timeStr, acc))
                delay(3500); onExit()
            }
        }

        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        if (gameStatus == "PLAYING") {
                            if (change.pressed && !change.previousPressed) {
                                bullets.add(Bullet(Offset(shipPos.x, shipPos.y - 60f), Color.White, 150))
                                firedCount++; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (change.pressed) {
                                val m = change.positionChange()
                                shipPos = Offset((shipPos.x + m.x).coerceIn(50f, sw - 50f), (shipPos.y + m.y).coerceIn(sh*0.3f, sh - 100f))
                                change.consume()
                            }
                        }
                    }
                }
            }
        }) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val coreRadiusPx = 130.dp.toPx()
                val outerRadiusPx = 150.dp.toPx()
                
                if (gameStatus != "WON") {
                    val pulse = sin(gameTimeByFrame / 150f) * 0.15f
                    drawCircle(Color.White.copy(0.08f), radius = outerRadiusPx * (1f + pulse), center = bossPos)
                    drawCircle(Color.White, radius = coreRadiusPx, center = bossPos, style = Stroke(2.5.dp.toPx()))
                    drawCircle(Color.Black, radius = coreRadiusPx - 2.dp.toPx(), center = bossPos)
                    drawCircle(Color.White.copy(0.6f), radius = 50.dp.toPx(), center = bossPos, style = Stroke(1.dp.toPx()))
                    rotate(gameTimeByFrame / 8f, pivot = bossPos) {
                        drawArc(Color.White.copy(0.4f), 0f, 60f, false, bossPos - Offset(160.dp.toPx(), 160.dp.toPx()), Size(320.dp.toPx(), 320.dp.toPx()), style = Stroke(2.dp.toPx()))
                        drawArc(Color.White.copy(0.4f), 180f, 60f, false, bossPos - Offset(160.dp.toPx(), 160.dp.toPx()), Size(320.dp.toPx(), 320.dp.toPx()), style = Stroke(2.dp.toPx()))
                    }
                    val gRect = Rect(bossPos.x - 50.dp.toPx(), bossPos.y - 50.dp.toPx(), bossPos.x + 50.dp.toPx(), bossPos.y + 50.dp.toPx())
                    drawArc(Color.White, 35f, 290f, false, gRect.topLeft, gRect.size, style = Stroke(8.dp.toPx()))
                    drawLine(Color.White, bossPos, Offset(bossPos.x + 50.dp.toPx(), bossPos.y), strokeWidth = 8.dp.toPx())
                }
                
                if (gameStatus != "LOST") {
                    val dAlpha = 1f - breakdownProgress.value
                    val dExpand = breakdownProgress.value * 80f
                    rotate(0f, pivot = shipPos) {
                        val shipPath = Path().apply { moveTo(shipPos.x, shipPos.y-50f - dExpand); lineTo(shipPos.x-35f - dExpand, shipPos.y+30f + dExpand); lineTo(shipPos.x, shipPos.y+12f); lineTo(shipPos.x+35f + dExpand, shipPos.y+30f + dExpand); close() }
                        drawPath(shipPath, Color.White.copy(alpha = dAlpha), style = Stroke(2.dp.toPx()))
                    }
                }

                bullets.forEach { b -> if (b.isEnemy) drawCircle(Color.White, 10f, b.position) else drawRect(Color.White, b.position-Offset(2.5f,15f), Size(5f, 30f)) }
                particles.forEach { p -> drawCircle(p.color.copy(alpha = p.life), radius = 3f, center = p.pos) }
            }
            // HUD
            Column(modifier = Modifier.padding(24.dp).align(Alignment.TopStart)) {
                Text("AXION COMBAT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ORB: ", color = Color.White.copy(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.width(160.dp).height(8.dp).background(Color.White.copy(0.1f))) { Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animH).background(Color.White)) }
                    Text("  ${bossHealth.toInt()}/2500", color = Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(24.dp))
                logs.forEach { Text(it.message, color = Color.White.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            }
            Column(modifier = Modifier.padding(24.dp).align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                val ms = (gameTime/10 % 100); val s = (gameTime/1000 % 60); val m = (gameTime/60000)
                Text(text = "T+ ${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}:${ms.toString().padStart(2,'0')}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌖ ", color = Color.White, fontSize = 12.sp)
                    Text(text = "X:${shipPos.x.toInt()} Y:${shipPos.y.toInt()}", color = Color.White.copy(0.5f), fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun SummaryScreen(statsManager: StatsManager, onExit: () -> Unit) {
    val lastStat = statsManager.history.firstOrNull()
    val isWin = lastStat?.result == "WON"
    val lastTime = lastStat?.time ?: "00:00:00"
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = if (isWin) "PURITY RESTORED" else "INTEGRITY BREACHED", 
                color = Color.White, 
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = if (isWin) 12.sp else 8.sp, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isWin) "EXTERNAL INFLUENCE NEUTRALIZED" else "SYSTEM PURITY COMPROMISED",
                color = Color.White.copy(0.4f),
                style = TextStyle(fontSize = 10.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(60.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MISSION TIME", color = Color.White.copy(0.3f), fontSize = 10.sp, letterSpacing = 4.sp)
                Text(lastTime, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Thin)
            }
            Spacer(Modifier.height(60.dp))
            Text("TACTICAL DATA // MISSION_LOG", color = Color.White.copy(0.2f), fontSize = 9.sp, letterSpacing = 6.sp)
            Spacer(Modifier.height(20.dp))
            Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                LazyColumn {
                    items(statsManager.history) { stat ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(SimpleDateFormat("HH:mm", Locale.US).format(Date(stat.timestamp)), color = Color.White.copy(0.3f), fontSize = 10.sp, modifier = Modifier.width(60.dp))
                            Text(text = if (stat.result == "WON") "PURGED" else "FAILED", color = if (stat.result == "WON") Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.width(80.dp))
                            Text(text = stat.time, color = Color.White.copy(0.6f), fontSize = 10.sp, modifier = Modifier.width(70.dp))
                            Text("${stat.accuracy}% ACC", color = Color.White.copy(0.4f), fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
            Text(text = "TAP TO RE-INITIALIZE", modifier = Modifier.clickable { onExit() }, style = TextStyle(fontSize = 11.sp, color = Color.White.copy(0.4f), letterSpacing = 4.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun DamageKeyRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp).width(200.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color)); Spacer(Modifier.width(20.dp))
        Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    }
}

@Composable
fun AtomCrashGame(onFinish: () -> Unit) {
    val haptic = LocalHapticFeedback.current; val scope = rememberCoroutineScope()
    var remainingAtoms by remember { mutableIntStateOf(5) }
    var isExploding by remember { mutableStateOf(false) }
    val nScale = remember { Animatable(1f) }; val nShake = remember { Animatable(0f) }
    val boom = remember { Animatable(0f) }
    val rotation by rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)))

    Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        if (remainingAtoms > 0 && !isExploding) {
            scope.launch {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                launch { nScale.animateTo(1.3f, tween(60)); nScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
                launch { repeat(4) { nShake.animateTo(15f, tween(30)); nShake.animateTo(-15f, tween(30)) }; nShake.animateTo(0f, tween(30)) }
            }
            remainingAtoms--
            if (remainingAtoms == 0) { isExploding = true; scope.launch { boom.animateTo(1f, tween(1000, easing = ExpoOut)); onFinish() } }
        }
    }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(450.dp).graphicsLayer { translationX = nShake.value }) {
            val center = Offset(size.width / 2, size.height / 2)
            if (boom.value < 1f) {
                val alpha = (1f - boom.value)
                drawCircle(Color.White.copy(alpha), radius = 60.dp.toPx() * nScale.value, center = center, style = Stroke(2.dp.toPx()))
                drawCircle(Color.Black.copy(alpha), radius = 58.dp.toPx() * nScale.value, center = center)
                drawCircle(Color.White.copy(0.6f * alpha), radius = 25.dp.toPx() * nScale.value, center = center, style = Stroke(1.dp.toPx()))
                if (isExploding) drawCircle(Color.White.copy(0.5f * alpha), radius = boom.value * size.maxDimension, center = center, style = Stroke(2.dp.toPx()))
                for (i in 0 until 3) rotate(i * 60f, pivot = center) { drawOval(Color.White.copy(0.12f * alpha), center - Offset(size.width/2.8f, size.width/7f), Size(size.width/1.4f, size.width/3.5f), style = Stroke(1.dp.toPx())) }
                val angles = listOf(0f, 72f, 144f, 216f, 288f)
                if (!isExploding) for (i in 0 until remainingAtoms) {
                    val rad = Math.toRadians((rotation + angles[i]).toDouble())
                    val x = center.x + cos(rad).toFloat() * 150.dp.toPx(); val y = center.y + sin(rad).toFloat() * 60.dp.toPx()
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, y))
                }
            }
        }
    }
}

private fun DrawScope.drawElectronOnOrbit(cX: Float, cY: Float, rX: Float, rY: Float, tilt: Float, angle: Float, radius: Float, color: Color, crashProgress: Float) {
    val rad = Math.toRadians(angle.toDouble()); val tiltRad = Math.toRadians(tilt.toDouble())
    val x = rX * cos(rad).toFloat(); val y = rY * sin(rad).toFloat()
    val rotX = x * cos(tiltRad).toFloat() - y * sin(tiltRad).toFloat()
    val rotY = x * sin(tiltRad).toFloat() + y * cos(tiltRad).toFloat()
    val eX = cX + (rotX * (1f - crashProgress)); val eY = cY + (rotY * (1f - crashProgress))
    drawCircle(Color.White.copy(0.15f), radius = radius * 3f, center = Offset(eX, eY))
    drawCircle(color, radius, center = Offset(eX, eY), style = Stroke(1.5.dp.toPx()))
}
