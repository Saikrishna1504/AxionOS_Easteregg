package com.axion.os.easteregg.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val alpha: Float)
data class Bullet(var position: Offset, val isEnemy: Boolean = false, val velocityX: Float = 0f)
data class Particle(var pos: Offset, val vel: Offset, val color: Color, var life: Float, val size: Float = 4f)
data class Shockwave(var radius: Float, var alpha: Float, val pos: Offset)
data class DamageText(val text: String, var pos: Offset, var life: Float) 
data class WarpLine(var x: Float, var y: Float, val speed: Float, val length: Float) 
data class GameStat(val timestamp: Long, val result: String, val time: String, val accuracy: Int)
