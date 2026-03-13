package com.axion.os.easteregg

import android.content.Context
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
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

val ExpoOut = Easing { x -> if (x == 1f) 1f else 1f - Math.pow(2.0, -10.0 * x.toDouble()).toFloat() }
val BackEaseIn = Easing { x -> val s = 1.70158f; x * x * ((s + 1) * x - s) }

data class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val alpha: Float)
data class Bullet(var position: Offset, val isEnemy: Boolean = false, val velocityX: Float = 0f)
data class Particle(var pos: Offset, val vel: Offset, val color: Color, var life: Float, val size: Float = 4f)
data class Shockwave(var radius: Float, var alpha: Float, val pos: Offset)
data class DamageText(val text: String, var pos: Offset, var life: Float) 
data class WarpLine(var x: Float, var y: Float, val speed: Float, val length: Float) 

data class GameStat(val timestamp: Long, val result: String, val time: String, val accuracy: Int)

class StatsManager(context: Context) {
    private val prefs = context.getSharedPreferences("axion_stats_v2", Context.MODE_PRIVATE)
    
    var history by mutableStateOf(getHistoryFromPrefs())
        private set
    var lifetimeWins by mutableIntStateOf(prefs.getInt("total_wins", 0))
        private set
    var lifetimeGames by mutableIntStateOf(prefs.getInt("total_games", 0))
        private set

    fun saveStat(stat: GameStat) {
        val currentHistory = getHistoryFromPrefs().toMutableList()
        currentHistory.add(0, stat)
        val limitedHistory = currentHistory.take(10)
        
        val array = JSONArray()
        limitedHistory.forEach {
            val obj = JSONObject()
            obj.put("ts", it.timestamp)
            obj.put("res", it.result)
            obj.put("time", it.time)
            obj.put("acc", it.accuracy)
            array.put(obj)
        }
        
        val newWins = prefs.getInt("total_wins", 0) + (if (stat.result == "WON") 1 else 0)
        val newGames = prefs.getInt("total_games", 0) + 1
        
        prefs.edit().putString("history", array.toString()).putInt("total_wins", newWins).putInt("total_games", newGames).apply()
            
        history = limitedHistory
        lifetimeWins = newWins
        lifetimeGames = newGames
    }
    
    private fun getHistoryFromPrefs(): List<GameStat> {
        val json = prefs.getString("history", null) ?: return emptyList()
        val list = mutableListOf<GameStat>()
        val array = JSONArray(json)
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
    
    // Reduced Parallax Layers
    val layer1 = remember { List(30) { Star(Random.nextFloat(), Random.nextFloat(), 0.8f, 0.05f, 0.15f) } }
    val layer2 = remember { List(15) { Star(Random.nextFloat(), Random.nextFloat(), 1.2f, 0.12f, 0.35f) } }
    val layer3 = remember { List(8) { Star(Random.nextFloat(), Random.nextFloat(), 2.0f, 0.25f, 0.6f) } }
    
    val starOffset by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)))
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val drawLayer = { stars: List<Star>, speedMult: Float ->
                stars.forEach { star ->
                    val currentY = (star.y + starOffset * star.speed * speedMult) % 1f
                    drawCircle(Color.White.copy(alpha = star.alpha), radius = star.size, center = Offset(star.x * size.width, currentY * size.height))
                }
            }
            drawLayer(layer1, 10f)
            drawLayer(layer2, 15f)
            drawLayer(layer3, 25f)
        }

        Crossfade(targetState = gameState, animationSpec = tween(1000), label = "state") { state ->
            when(state) {
                "SPLASH" -> SplashScreen(onFinish = { gameState = "ANIMATION" })
                "ANIMATION" -> AtomCrashGame(onFinish = { gameState = "PREP" })
                "PREP" -> CombatPrepScreen(onStart = { gameState = "GAME" })
                "GAME" -> SpaceGame(statsManager = statsManager, onExit = { gameState = "SPLASH" })
            }
        }
    }
}

@Composable
fun CombatPrepScreen(onStart: () -> Unit) {
    var text1Chars by remember { mutableIntStateOf(0) }
    var text2Chars by remember { mutableIntStateOf(0) }
    val fullText1 = "SYNCING FLIGHT COURSE..."
    val fullText2 = "AXION COURSE PREPARED"
    
    val verticalBias = remember { Animatable(0f) }
    val shipYOffset = remember { Animatable(500f) } 
    val shipAlpha = remember { Animatable(0f) }
    
    val infiniteTransition = rememberInfiniteTransition()
    val thrusterScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(60, easing = LinearEasing), RepeatMode.Reverse),
        label = "thruster"
    )

    LaunchedEffect(Unit) {
        while (text1Chars < fullText1.length) {
            delay(80)
            text1Chars++
        }
        delay(800)
        
        verticalBias.animateTo(-0.4f, tween(1000, easing = FastOutSlowInEasing))
        delay(600)
        
        while (text2Chars < fullText2.length) {
            delay(60)
            text2Chars++
        }
        delay(800)

        launch { shipAlpha.animateTo(1f, tween(1000)) }
        shipYOffset.animateTo(0f, tween(1500, easing = LinearOutSlowInEasing))
        
        while(true) {
            shipYOffset.animateTo(-15f, tween(2000, easing = FastOutSlowInEasing))
            shipYOffset.animateTo(0f, tween(2000, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
        if (text2Chars >= fullText2.length) onStart() 
    }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .align(BiasAlignment(0f, verticalBias.value)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = fullText1.take(text1Chars),
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp, color = Color.White, fontFamily = FontFamily.Monospace),
                textAlign = TextAlign.Center
            )

            if (verticalBias.value < -0.1f) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = fullText2.take(text2Chars),
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White, fontFamily = FontFamily.Monospace),
                    textAlign = TextAlign.Center
                )
                
                if (text2Chars >= fullText2.length) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "[ SYSTEM_NOTE: DO NOT TAP 5 TIMES OVER \"AXION COMBAT\" ]",
                        style = TextStyle(fontSize = 10.sp, color = Color.White.copy(0.25f), fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // IMPROVED INTERCEPTOR SHIP
        Box(modifier = Modifier
            .align(Alignment.Center)
            .offset(y = 220.dp + shipYOffset.value.dp)
            .graphicsLayer { alpha = shipAlpha.value }
            .size(120.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                // Thruster Flame Animation
                val flamePath = Path().apply {
                    moveTo(w * 0.45f, h * 0.8f)
                    lineTo(w * 0.5f, h * (0.8f + 0.25f * thrusterScale))
                    lineTo(w * 0.55f, h * 0.8f)
                    close()
                }
                drawPath(flamePath, Color.White.copy(0.6f))
                drawCircle(Color.White.copy(0.3f), radius = 12.dp.toPx() * thrusterScale, center = Offset(w * 0.5f, h * 0.82f))

                // Heavy Interceptor Hull
                val bodyPath = Path().apply {
                    moveTo(w * 0.5f, h * 0.1f)   // Nose tip
                    lineTo(w * 0.58f, h * 0.35f) // Right nose
                    lineTo(w * 0.85f, h * 0.65f) // Right wing top
                    lineTo(w * 0.92f, h * 0.85f) // Right wing tip
                    lineTo(w * 0.72f, h * 0.85f) // Right wing base
                    lineTo(w * 0.62f, h * 0.72f) // Right engine intake
                    lineTo(w * 0.5f, h * 0.8f)   // Rear thruster port
                    lineTo(w * 0.38f, h * 0.72f) // Left engine intake
                    lineTo(w * 0.28f, h * 0.85f) // Left wing base
                    lineTo(w * 0.08f, h * 0.85f) // Left wing tip
                    lineTo(w * 0.15f, h * 0.65f) // Left wing top
                    lineTo(w * 0.42f, h * 0.35f) // Left nose
                    close()
                }
                
                // Main Structure
                drawPath(bodyPath, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                
                // Hull Reinforcements / Details
                drawLine(Color.White.copy(0.5f), Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.5f), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(0.4f), Offset(w * 0.4f, h * 0.5f), Offset(w * 0.6f, h * 0.5f), strokeWidth = 1.dp.toPx())
                
                // Cockpit
                val cockpit = Path().apply {
                    moveTo(w * 0.46f, h * 0.38f)
                    lineTo(w * 0.54f, h * 0.38f)
                    lineTo(w * 0.52f, h * 0.5f)
                    lineTo(w * 0.48f, h * 0.5f)
                    close()
                }
                drawPath(cockpit, Color.White.copy(0.2f))
                drawPath(cockpit, Color.White, style = Stroke(1.dp.toPx()))
            }
        }

        if (text2Chars >= fullText2.length) {
            Text(
                text = "TAP TO INITIATE",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
                style = TextStyle(fontSize = 12.sp, color = Color.White.copy(0.4f), letterSpacing = 6.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            )
        }
    }
}

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    var currentWordIndex by remember { mutableIntStateOf(0) }
    val animatedWords = listOf("more faster.", "more powerful.", "more reliable.", "more axion.")
    
    LaunchedEffect(Unit) {
        delay(1500)
        currentWordIndex = 1
        delay(1500)
        currentWordIndex = 2
        delay(1500)
        currentWordIndex = 3
        delay(2000)
        onFinish()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Axion", style = TextStyle(fontSize = 58.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White))
                Text(text = "OS", style = TextStyle(fontSize = 58.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White, drawStyle = Stroke(width = 3f)))
            }

            Spacer(Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
                    Text("Make your android ", style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(0.6f)))
                    
                    AnimatedContent(
                        targetState = currentWordIndex,
                        transitionSpec = {
                            (slideInVertically { height -> height } + fadeIn(tween(600))).togetherWith(
                                slideOutVertically { height -> -height } + fadeOut(tween(600))
                            )
                        },
                        label = "word_carousel"
                    ) { index ->
                        Text(text = animatedWords[index], style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (index == 3) Color.White else Color.White.copy(0.6f)))
                    }
                }
            }
        }
    }
}

@Composable
fun SpaceGame(statsManager: StatsManager, onExit: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        
        var shipPos by remember { mutableStateOf(Offset(screenWidth / 2, screenHeight * 0.85f)) }
        var shipLean by remember { mutableFloatStateOf(0f) }
        var isMoving by remember { mutableStateOf(false) } 
        var fireFlash by remember { mutableFloatStateOf(0f) }
        
        var bossPos by remember { mutableStateOf(Offset(screenWidth / 2, screenHeight * 0.18f)) }
        var bossPhase by remember { mutableFloatStateOf(0f) } 
        
        var bossHealth by remember { mutableFloatStateOf(2026f) }
        var bossHitFlash by remember { mutableFloatStateOf(0f) } 
        
        var gameStatus by remember { mutableStateOf("PLAYING") } 
        
        val bullets = remember { mutableStateListOf<Bullet>() }
        val particles = remember { mutableStateListOf<Particle>() }
        val shockwaves = remember { mutableStateListOf<Shockwave>() } 
        val damageTexts = remember { mutableStateListOf<DamageText>() } 
        
        val warpLines = remember { 
            mutableStateListOf<WarpLine>().apply {
                repeat(25) { add(WarpLine(Random.nextFloat() * screenWidth, Random.nextFloat() * screenHeight, Random.nextFloat() * 15f + 10f, Random.nextFloat() * 80f + 20f)) }
            }
        }
        
        // SECRET PROTOCOL STATE
        var secretTaps by remember { mutableIntStateOf(0) }
        var overdriveMode by remember { mutableStateOf(false) }
        val shipAccentColor = Color.White
        val thrusterColor = Color.White.copy(alpha = 0.7f)
        
        var gameTime by remember { mutableLongStateOf(0L) }
        var lastFrameTime by remember { mutableLongStateOf(-1L) }
        
        val destructionProgress = remember { Animatable(0f) }
        val screenShake = remember { Animatable(0f) }
        
        val infiniteTransition = rememberInfiniteTransition()
        val thrusterScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(60, easing = LinearEasing), RepeatMode.Reverse),
            label = "thruster"
        )
        
        var bulletsFired by remember { mutableIntStateOf(0) }
        var hitsConfirmed by remember { mutableIntStateOf(0) }
        val accuracy = derivedStateOf { if (bulletsFired > 0) ((hitsConfirmed.toFloat() / bulletsFired) * 100).toInt() else 0 }

        val animatedHealth by animateFloatAsState(targetValue = bossHealth / 2026f, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "health")
        
        val envColor by animateColorAsState(
            targetValue = if (bossHealth < 500f) Color.Gray else Color.White,
            animationSpec = tween(1500), label = "env_color"
        )

        var targetBossPos by remember { mutableStateOf(Offset(screenWidth / 2, screenHeight * 0.2f)) }
        var bossVelocity by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(Unit) {
            var lastBossShot = 0L
            var lastTargetChange = 0L
            
            while(gameStatus == "PLAYING") {
                withFrameMillis { frameTime ->
                    if (lastFrameTime == -1L) lastFrameTime = frameTime
                    val deltaMs = frameTime - lastFrameTime
                    lastFrameTime = frameTime
                    gameTime += deltaMs
                    
                    if (bossHitFlash > 0f) bossHitFlash = max(0f, bossHitFlash - 0.1f)
                    if (fireFlash > 0f) fireFlash = max(0f, fireFlash - 0.15f)

                    // Smooth Horizontal Sine Movement
                    val targetSpeed = when {
                        bossHealth > 1500f -> 1.3f  
                        bossHealth > 1000f -> 1.7f
                        bossHealth > 500f -> 2.2f
                        else -> 2.8f              
                    }
                    bossPhase += (deltaMs / 1000f) * targetSpeed

                    bossPos = Offset(
                        x = (screenWidth / 2) + sin(bossPhase) * (screenWidth * 0.35f),
                        y = (screenHeight * 0.18f) + sin(bossPhase * 2.1f) * 15f 
                    )

                    // Hull Collision Detection
                    if ((shipPos - bossPos).getDistance() < 160f) { 
                         gameStatus = "LOST"
                         launch { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            launch { 
                                repeat(6) { screenShake.animateTo((15..25).random().toFloat(), tween(30)); screenShake.animateTo(-(15..25).random().toFloat(), tween(30)) }
                                screenShake.animateTo(0f)
                            }
                            destructionProgress.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
                        }
                    }

                    if (gameTime % 2L == 0L) {
                        val spread = if (isMoving) 15f else 5f
                        particles.add(Particle(pos = shipPos + Offset(Random.nextFloat() * spread - (spread/2), 40f), vel = Offset(0f, Random.nextFloat() * 5f + 5f), color = thrusterColor.copy(alpha = 0.6f), life = 1f, size = Random.nextFloat() * 3f + 1f))
                    }
                    
                    warpLines.forEach { w ->
                        w.y += w.speed
                        if (w.y > screenHeight + w.length) {
                            w.y = -w.length
                            w.x = Random.nextFloat() * screenWidth
                        }
                    }

                    val fireDelay = when {
                        bossHealth > 1500f -> 1600L 
                        bossHealth > 1000f -> 1400L 
                        bossHealth > 500f -> 1200L  
                        else -> 1300L 
                    }

                    if (gameTime - lastBossShot > fireDelay) {
                        when {
                            bossHealth <= 1000f -> { 
                                bullets.add(Bullet(bossPos + Offset(0f, 100f), isEnemy = true, velocityX = 0f))
                                bullets.add(Bullet(bossPos + Offset(-30f, 100f), isEnemy = true, velocityX = -5f)) 
                                bullets.add(Bullet(bossPos + Offset(30f, 100f), isEnemy = true, velocityX = 5f))
                            }
                            bossHealth <= 1500f -> { 
                                bullets.add(Bullet(bossPos + Offset(-20f, 100f), isEnemy = true, velocityX = -3f))
                                bullets.add(Bullet(bossPos + Offset(20f, 100f), isEnemy = true, velocityX = 3f))
                            }
                            else -> {
                                bullets.add(Bullet(bossPos + Offset(0f, 100f), isEnemy = true, velocityX = 0f))
                            }
                        }
                        lastBossShot = gameTime
                    }
                    
                    val bIterator = bullets.listIterator()
                    while(bIterator.hasNext()) {
                        val b = bIterator.next()
                        if (b.isEnemy) {
                            b.position = Offset(b.position.x + b.velocityX, b.position.y + 10f) 
                            if ((b.position - shipPos).getDistance() < 55f) {
                                gameStatus = "LOST"
                                launch { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    launch { 
                                        repeat(6) { screenShake.animateTo((15..25).random().toFloat(), tween(30)); screenShake.animateTo(-(15..25).random().toFloat(), tween(30)) }
                                        screenShake.animateTo(0f)
                                    }
                                    destructionProgress.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
                                }
                            }
                        } else {
                            b.position = Offset(b.position.x, b.position.y - 48f)
                            if ((b.position - bossPos).getDistance() < 130f) {
                                val damageAmount = if (overdriveMode) 100f else 25f
                                bossHealth = (bossHealth - damageAmount).coerceAtLeast(0f)
                                hitsConfirmed++
                                bossHitFlash = 1f 
                                shockwaves.add(Shockwave(radius = 60f, alpha = 0.8f, pos = b.position)) 
                                
                                damageTexts.add(DamageText("-${damageAmount.toInt()}", bossPos + Offset(Random.nextFloat() * 80 - 40, Random.nextFloat() * 40 - 60), 1f))
                                
                                bIterator.remove()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) 
                                repeat(4) { particles.add(Particle(b.position, Offset(Random.nextFloat()*20-10, Random.nextFloat()*20-10), Color.White, 1f)) }
                                if (bossHealth <= 0) {
                                    bossHealth = 0f
                                    gameStatus = "WON"
                                    launch { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        launch { 
                                            repeat(8) { screenShake.animateTo((20..30).random().toFloat(), tween(30)); screenShake.animateTo(-(20..30).random().toFloat(), tween(30)) }
                                            screenShake.animateTo(0f)
                                        }
                                        destructionProgress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
                                    }
                                }
                                continue 
                            }
                        }
                        if (b.position.y < -150f || b.position.y > screenHeight + 150f) bIterator.remove()
                    }
                    
                    val pIterator = particles.listIterator()
                    while(pIterator.hasNext()) {
                        val p = pIterator.next()
                        p.pos += p.vel
                        p.life -= 0.05f
                        if (p.life <= 0) pIterator.remove()
                    }

                    val sIterator = shockwaves.listIterator()
                    while(sIterator.hasNext()) {
                        val s = sIterator.next()
                        s.radius += 6f
                        s.alpha -= 0.06f
                        if (s.alpha <= 0) sIterator.remove()
                    }
                    
                    val dtIterator = damageTexts.listIterator()
                    while(dtIterator.hasNext()) {
                        val dt = dtIterator.next()
                        dt.pos = Offset(dt.pos.x, dt.pos.y - 1.5f) 
                        dt.life -= 0.02f 
                        if (dt.life <= 0) dtIterator.remove()
                    }
                }
            }
            
            if (gameStatus != "PLAYING") {
                val centis = (gameTime % 1000) / 10
                val secs = (gameTime / 1000) % 60
                val mins = (gameTime / 60000)
                val timeStr = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}:${centis.toString().padStart(2, '0')}"
                statsManager.saveStat(GameStat(System.currentTimeMillis(), gameStatus, timeStr, accuracy.value))
                delay(1000) 
                gameStatus = "LEADERBOARD"
            }
        }

        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        if (gameStatus == "PLAYING") {
                            if (change.pressed && !change.previousPressed) {
                                bullets.add(Bullet(Offset(shipPos.x, shipPos.y - 60f)))
                                bulletsFired++
                                fireFlash = 1f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (change.pressed) {
                                isMoving = true
                                val move = change.positionChange()
                                shipLean = (move.x * 0.8f).coerceIn(-25f, 25f)
                                shipPos = Offset((shipPos.x + move.x).coerceIn(60f, screenWidth - 60f), (shipPos.y + move.y).coerceIn(200f, screenHeight - 120f))
                                change.consume()
                            } else {
                                isMoving = false
                                shipLean = 0f
                            }
                        }
                    }
                }
            }
        }) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { 
                translationX = screenShake.value 
                translationY = if (screenShake.value != 0f) (screenShake.value * 0.5f) else 0f
            }) {
                val parallaxX = (screenWidth / 2 - shipPos.x) * 0.04f
                val parallaxY = (screenHeight / 2 - shipPos.y) * 0.04f
                
                if (gameStatus == "PLAYING" || gameStatus == "WON") {
                    val streamColor = envColor.copy(alpha = 0.1f)
                    warpLines.forEach { w ->
                        drawLine(color = streamColor, start = Offset(w.x, w.y), end = Offset(w.x, w.y + w.length), strokeWidth = 2f, cap = StrokeCap.Round)
                    }
                }

                shockwaves.forEach { s ->
                    drawCircle(color = Color.White.copy(alpha = s.alpha), radius = s.radius, center = s.pos, style = Stroke(2f))
                }

                // --- BOSS DRAWING ---
                if (gameStatus == "PLAYING" || gameStatus == "LOST" || (gameStatus == "WON" && destructionProgress.value < 1f)) {
                    val dAlpha = if (gameStatus == "WON") (1f - destructionProgress.value) else 1f
                    val dScale = if (gameStatus == "WON") (1f + destructionProgress.value * 0.5f) else 1f
                    val baseR = 120f * dScale
                    val pulse = if (gameStatus == "PLAYING") sin(gameTime / 180f) * 0.1f else 0f
                    
                    if (bossHitFlash > 0f) {
                        val glitchOffset = Offset((Random.nextFloat() - 0.5f) * 20f, (Random.nextFloat() - 0.5f) * 20f)
                        drawCircle(Color.White.copy(alpha = 0.3f * bossHitFlash * dAlpha), radius = baseR, center = bossPos + glitchOffset, style = Stroke(4f))
                        drawCircle(Color.White.copy(alpha = 0.3f * bossHitFlash * dAlpha), radius = baseR, center = bossPos - glitchOffset, style = Stroke(4f))
                    }

                    drawCircle(Color.White.copy(0.12f * dAlpha), radius = 260f * (1f + pulse) * dScale, center = bossPos)
                    drawCircle(Color.White.copy(dAlpha), radius = baseR, center = bossPos, style = Stroke(3.dp.toPx()))
                    
                    rotate(gameTime / 8f, pivot = bossPos) {
                        val arcRadius = 135f * dScale
                        drawArc(Color.White.copy(0.4f * dAlpha), 0f, 60f, false, bossPos - Offset(arcRadius, arcRadius), Size(arcRadius * 2, arcRadius * 2), style = Stroke(2.dp.toPx()))
                        drawArc(Color.White.copy(0.4f * dAlpha), 180f, 60f, false, bossPos - Offset(arcRadius, arcRadius), Size(arcRadius * 2, arcRadius * 2), style = Stroke(2.dp.toPx()))
                    }
                    
                    val gSize = 55f * dScale
                    val gRect = Rect(bossPos.x - gSize, bossPos.y - gSize, bossPos.x + gSize, bossPos.y + gSize)
                    drawArc(Color.White.copy(dAlpha), 35f, 290f, false, gRect.topLeft, gRect.size, style = Stroke(7.dp.toPx() * dAlpha.coerceAtLeast(0.1f)))
                    // Small stylized bar for 'G'
                    drawLine(Color.White.copy(dAlpha), Offset(bossPos.x + 15f * dScale, bossPos.y), Offset(bossPos.x + gSize, bossPos.y), strokeWidth = 7.dp.toPx())
                }

                // --- SHIP DRAWING ---
                if (gameStatus == "PLAYING" || gameStatus == "WON" || (gameStatus == "LOST" && destructionProgress.value < 1f)) {
                    val dAlpha = if (gameStatus == "LOST") (1f - destructionProgress.value) else 1f
                    val dExpand = if (gameStatus == "LOST") destructionProgress.value * 100f else 0f
                    
                    rotate(shipLean, pivot = shipPos) {
                        translate(left = shipPos.x - 48f, top = shipPos.y - 48f) {
                            val w = 96f // 120dp * 0.8
                            val h = 96f
                            
                            // Thruster Flame Animation
                            val flamePath = Path().apply {
                                moveTo(w * 0.45f, h * 0.8f + dExpand)
                                lineTo(w * 0.5f, h * (0.8f + 0.25f * thrusterScale) + dExpand)
                                lineTo(w * 0.55f, h * 0.8f + dExpand)
                                close()
                            }
                            drawPath(flamePath, Color.White.copy(0.6f * dAlpha))
                            drawCircle(Color.White.copy(0.3f * dAlpha), radius = 7.dp.toPx() * thrusterScale, center = Offset(w * 0.5f, h * 0.82f + dExpand))

                            // Heavy Interceptor Hull
                            val bodyPath = Path().apply {
                                moveTo(w * 0.5f, h * 0.1f - dExpand)   // Nose tip
                                lineTo(w * 0.58f, h * 0.35f) // Right nose
                                lineTo(w * 0.85f, h * 0.65f + dExpand) // Right wing top
                                lineTo(w * 0.92f, h * 0.85f + dExpand) // Right wing tip
                                lineTo(w * 0.72f, h * 0.85f + dExpand) // Right wing base
                                lineTo(w * 0.62f, h * 0.72f + dExpand) // Right engine intake
                                lineTo(w * 0.5f, h * 0.8f + dExpand)   // Rear thruster port
                                lineTo(w * 0.38f, h * 0.72f + dExpand) // Left engine intake
                                lineTo(w * 0.28f, h * 0.85f + dExpand) // Left wing base
                                lineTo(w * 0.08f, h * 0.85f + dExpand) // Left wing tip
                                lineTo(w * 0.15f, h * 0.65f + dExpand) // Left wing top
                                lineTo(w * 0.42f, h * 0.35f) // Left nose
                                close()
                            }
                            
                            // Main Structure
                            drawPath(bodyPath, Color.White.copy(dAlpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            
                            // Hull Details
                            drawLine(Color.White.copy(0.5f * dAlpha), Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.5f), strokeWidth = 1.dp.toPx())
                            
                            // Cockpit
                            val cockpit = Path().apply {
                                moveTo(w * 0.46f, h * 0.38f)
                                lineTo(w * 0.54f, h * 0.38f)
                                lineTo(w * 0.52f, h * 0.5f)
                                lineTo(w * 0.48f, h * 0.5f)
                                close()
                            }
                            drawPath(cockpit, Color.White.copy(0.2f * dAlpha))
                            drawPath(cockpit, Color.White.copy(dAlpha), style = Stroke(1.dp.toPx()))
                        }
                    }
                    
                    val coordsText = textMeasurer.measure(text = "X: ${shipPos.x.toInt().toString().padStart(4, '0')}\nY: ${shipPos.y.toInt().toString().padStart(4, '0')}", style = TextStyle(color = Color.White.copy(0.4f * dAlpha), fontSize = 10.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center))
                    drawText(textLayoutResult = coordsText, topLeft = Offset(shipPos.x - coordsText.size.width / 2, shipPos.y + 40f))
                }
                
                damageTexts.forEach { dt ->
                    val layout = textMeasurer.measure(dt.text, style = TextStyle(color = Color.White.copy(alpha = dt.life), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
                    drawText(layout, topLeft = dt.pos)
                }

                val damageColors = listOf(Color.White, Color.LightGray, Color.Gray, Color.DarkGray) 
                bullets.forEach { b -> 
                    if (b.isEnemy) {
                        val color = damageColors[bullets.indexOf(b) % damageColors.size]
                        drawCircle(color.copy(0.3f), radius = 12f, center = b.position) 
                        drawCircle(color, radius = 8f, center = b.position) 
                    } else { 
                        drawLine(color = shipAccentColor, start = b.position, end = Offset(b.position.x, b.position.y - 25f), strokeWidth = 4f, cap = StrokeCap.Round)
                    }
                }
                particles.forEach { p -> drawCircle(p.color.copy(alpha = Math.max(0f, p.life)), radius = p.size * p.life, center = p.pos) }
            }

            // --- HUD ---
            val liveTimeStr by remember {
                derivedStateOf {
                    val centis = (gameTime % 1000 / 10).toInt()
                    val secs = (gameTime / 1000 % 60).toInt()
                    val mins = (gameTime / 60000).toInt()
                    "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}:${centis.toString().padStart(2, '0')}"
                }
            }
            
            val missionStartTime: String = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).align(Alignment.TopStart), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(
                        text = if (overdriveMode) "OVERDRIVE PROTOCOL" else "AXION COMBAT", 
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            secretTaps++
                            if (secretTaps == 5) {
                                overdriveMode = !overdriveMode
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        color = shipAccentColor, 
                        style = TextStyle(letterSpacing = 2.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ORB", color = Color.White.copy(0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 8.dp))
                        Box(modifier = Modifier.width(140.dp).height(6.dp).background(Color.White.copy(0.1f))) {
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animatedHealth).background(Color.White)) 
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${bossHealth.toInt()}/2026", color = Color.White.copy(0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(16.dp))
                        Text(text = "ACC: ${accuracy.value}%", color = Color.White.copy(0.8f), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace, letterSpacing = 0.sp), maxLines = 1)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("$missionStartTime > MISSION START", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "T+ $liveTimeStr", color = Color.White, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.sp), maxLines = 1)
                }
            }

            if (gameStatus == "PLAYING") {
                Text("TOUCH TO MOVE • TAP OTHER FINGER TO FIRE", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), color = Color.White.copy(0.3f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            }
            
            if (gameStatus == "LEADERBOARD") {
                val dataStream = remember { List(20) { Random.nextInt(0x1000, 0xFFFF).toString(16).uppercase() } }
                val streamOffset by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)))
                
                var animState by remember { mutableIntStateOf(0) } // 0: Start, 1: Header, 2: Accuracy, 3: Log, 4: Button
                val headerText = if (gameStatus == "WON") "PURITY RESTORED" else "INTEGRITY BREACH"
                var headerVisibleChars by remember { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    // Stage 1: Header Typewriter
                    animState = 1
                    while (headerVisibleChars < headerText.length) {
                        delay(60)
                        headerVisibleChars++
                    }
                    delay(300)
                    
                    // Stage 2: Accuracy
                    animState = 2
                    delay(800)
                    
                    // Stage 3: Mission Log
                    animState = 3
                    delay(600)
                    
                    // Stage 4: Ready
                    animState = 4
                }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.98f))) {
                    // Background Data Stream
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        dataStream.forEachIndexed { i, hex ->
                            val xPos = (i * (size.width / 20))
                            val yPos = ((streamOffset * size.height) + (i * 100)) % size.height
                            drawText(
                                textMeasurer = textMeasurer,
                                text = hex,
                                topLeft = Offset(xPos, yPos),
                                style = TextStyle(color = Color.White.copy(0.05f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .border(1.dp, Color.White.copy(0.1f))
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header with Typewriter
                        Text(
                            text = headerText.take(headerVisibleChars), 
                            color = Color.White, 
                            style = TextStyle(fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, letterSpacing = 8.sp, textAlign = TextAlign.Center)
                        )
                        
                        AnimatedVisibility(
                            visible = animState >= 2,
                            enter = fadeIn(tween(1000)) + expandVertically(expandFrom = Alignment.Top)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (gameStatus == "WON") "EXTERNAL INFLUENCE NEUTRALIZED" else "SYSTEM PURITY COMPROMISED", 
                                    color = Color.White.copy(0.4f), 
                                    fontFamily = FontFamily.Monospace, 
                                    fontSize = 10.sp, 
                                    letterSpacing = 2.sp
                                )
                                
                                Spacer(Modifier.height(48.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).border(1.dp, Color.White.copy(0.4f)))
                                    Spacer(Modifier.width(16.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                         Text("FINAL ACCURACY", color = Color.White.copy(0.5f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 4.sp)
                                         Text("${accuracy.value}%", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.ExtraLight)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Box(modifier = Modifier.size(12.dp).border(1.dp, Color.White.copy(0.4f)))
                                }
                            }
                        }

                        Spacer(Modifier.height(48.dp))
                        
                        AnimatedVisibility(
                            visible = animState >= 3,
                            enter = fadeIn(tween(800)) + slideInVertically { it / 2 }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MISSION_LOG // SESSION_HISTORY", color = Color.White.copy(0.2f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 2.sp)
                                Spacer(Modifier.height(16.dp))
                                
                                Box(modifier = Modifier.height(180.dp).width(320.dp)) {
                                    LazyColumn(horizontalAlignment = Alignment.CenterHorizontally) {
                                        items(statsManager.history.take(10)) { stat ->
                                            val date = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(stat.timestamp))
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = date, color = Color.White.copy(0.3f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.width(90.dp))
                                                Text(text = stat.time, color = Color.White.copy(0.6f), fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.width(60.dp))
                                                Text(text = if (stat.result == "WON") "PURGED" else "FAILED", color = if (stat.result == "WON") Color.White else Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                                                Text(text = "${stat.accuracy}%", color = Color.White.copy(0.4f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.width(50.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(48.dp))
                        
                        AnimatedVisibility(
                            visible = animState >= 4,
                            enter = fadeIn(tween(1000))
                        ) {
                            Text(
                                text = "[ REINITIALIZE_CORE ]", 
                                modifier = Modifier.clickable { scope.launch { destructionProgress.snapTo(0f) }; onExit() }, 
                                color = Color.White, 
                                fontSize = 12.sp, 
                                fontFamily = FontFamily.Monospace, 
                                fontWeight = FontWeight.Bold, 
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AtomCrashGame(onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var remainingAtoms by remember { mutableIntStateOf(5) }
    var isExploding by remember { mutableStateOf(false) }
    
    val crashProgress = remember { Animatable(0f) }
    val explosionProgress = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }
    val nucleusShake = remember { Animatable(0f) }
    val nucleusScale = remember { Animatable(1f) }
    
    val slowGlobalRotation by rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(25000, easing = LinearEasing)))
    val corePulse by rememberInfiniteTransition().animateFloat(0.8f, 1.4f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val rotation by rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)))

    Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        if (remainingAtoms > 0 && !crashProgress.isRunning && !isExploding) {
            scope.launch {
                launch { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nucleusScale.animateTo(1.25f, tween(60)); nucleusScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
                launch { repeat(3) { nucleusShake.animateTo(8f, tween(30)); nucleusShake.animateTo(-8f, tween(30)) }; nucleusShake.animateTo(0f, tween(30)) }
                
                crashProgress.animateTo(1f, tween(250, easing = BackEaseIn))
                remainingAtoms--
                
                if (remainingAtoms == 0) {
                    isExploding = true
                    launch { flashAlpha.animateTo(0.5f, tween(40)); flashAlpha.animateTo(0f, tween(1200)) }
                    explosionProgress.animateTo(1f, tween(1500, easing = ExpoOut))
                    delay(1600)
                    onFinish()
                }
                crashProgress.snapTo(0f)
            }
        }
    }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(450.dp).graphicsLayer { translationX = nucleusShake.value }) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            if (!isExploding || (isExploding && explosionProgress.value < 1f)) {
                val alpha = (1f - explosionProgress.value).coerceAtLeast(0f)
                
                rotate(slowGlobalRotation, Offset(centerX, centerY)) {
                    if (alpha > 0f) {
                        for (i in 0 until 3) {
                            DrawOvalOrbit(centerX, centerY, size.minDimension/2.8f, size.minDimension/2.8f*0.45f, i*60f, Color.White.copy(0.15f*alpha), 1.5f.dp.toPx())
                        }
                    }
                    
                    val nRadius = (26.dp.toPx() * nucleusScale.value) + (if (isExploding) explosionProgress.value * 380.dp.toPx() else 0f)
                    
                    if (alpha > 0f) {
                        drawCircle(Color.White.copy(alpha = 0.1f * alpha), radius = nRadius * 1.5f * corePulse, center = Offset(centerX, centerY))
                        drawCircle(Color.White.copy(alpha), radius = nRadius, center = Offset(centerX, centerY))
                        drawCircle(Color.Black.copy(alpha), radius = nRadius*0.88f, center = Offset(centerX, centerY))
                        drawCircle(Color.White.copy(alpha*0.8f), radius = nRadius*0.35f, center = Offset(centerX, centerY))
                    }
                    
                    if (!isExploding) {
                        val tilts = listOf(0f, 60f, 120f, 0f, 60f)
                        val angles = listOf(0f, 72f, 144f, 216f, 288f)
                        for (i in 0 until remainingAtoms) {
                            DrawElectronOnOrbit(centerX, centerY, size.minDimension/2.8f, size.minDimension/2.8f*0.45f, tilts[i], rotation+angles[i], 8.dp.toPx(), Color.White, if (i == remainingAtoms-1 && crashProgress.value > 0f) crashProgress.value else 0f)
                        }
                    }
                }
                
                if (isExploding) {
                    drawCircle(Color.White.copy(0.4f*alpha), radius = explosionProgress.value * size.maxDimension, center = Offset(centerX, centerY), style = Stroke(1.dp.toPx()))
                }
            }
        }
        
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = flashAlpha.value)))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.DrawOvalOrbit(cX: Float, cY: Float, rX: Float, rY: Float, tilt: Float, color: Color, stroke: Float) {
    rotate(degrees = tilt, pivot = Offset(cX, cY)) { drawOval(color, Offset(cX - rX, cY - rY), Size(rX * 2, rY * 2), style = Stroke(stroke)) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.DrawElectronOnOrbit(cX: Float, cY: Float, rX: Float, rY: Float, tilt: Float, angle: Float, radius: Float, color: Color, crashProgress: Float) {
    val rad = Math.toRadians(angle.toDouble())
    val tiltRad = Math.toRadians(tilt.toDouble())
    val x = rX * cos(rad).toFloat()
    val y = rY * sin(rad).toFloat()
    val rotX = x * cos(tiltRad).toFloat() - y * sin(tiltRad).toFloat()
    val rotY = x * sin(tiltRad).toFloat() + y * cos(tiltRad).toFloat()
    val eX = cX + (rotX * (1f - crashProgress))
    val eY = cY + (rotY * (1f - crashProgress))
    
    drawCircle(Color.White.copy(0.2f), radius = radius * 2.5f, center = Offset(eX, eY))
    drawCircle(color, radius, center = Offset(eX, eY))
}
