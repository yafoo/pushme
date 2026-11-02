package com.android.pushme.components.message

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.Utils
import com.android.pushme.utils.PushMeApi.sendPushMeLog
import com.google.gson.Gson
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random


data class ChartData(
    var type: String = "bar", // 默认类型
    var size: UInt = 10u,     // 默认大小
    var list: MutableList<Float> = mutableListOf(),
    var label: MutableList<String> = mutableListOf()
)

@Composable
fun Chart(
    modifier: Modifier = Modifier,
    data: ChartData,
) {
    if(data.list == null || data.label == null || data.list.isEmpty()) {
        Text(
            text = "数据错误，或无有效数据",
            fontSize = 14.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8F),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        return
    }

    val yAxis = getYAxis(data.list)
    val colorScheme = MaterialTheme.colorScheme
    val colorList: List<Color> = remember(data.type, data.list.size) {
        val list = mutableListOf<Color>()
        if(data.type == "bar" || data.type == "pie") {
            data.list.forEachIndexed { index, _ ->
                list.add(
                    if(index == 0) colorScheme.primary else Color(
                        red = Random.nextFloat(),
                        green = Random.nextFloat(),
                        blue = Random.nextFloat()
                    ))
            }
        }
        list.toList()
    }
    val textMeasurer = rememberTextMeasurer() // 在 Composable 中调用

    Box(modifier = modifier.defaultMinSize(10.dp, 10.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (data.type) {
                "bar" -> {
                    drawYAxis(yAxis, colorScheme)
                    drawBarChart(yAxis, data.list, colorScheme, colorList, textMeasurer)
                    drawYLabels(yAxis, colorScheme, textMeasurer)
                    drawXLabels(data.label, colorScheme, textMeasurer)
                }
                "line" -> {
                    drawYAxis(yAxis, colorScheme)
                    drawLineChart(yAxis, data.list, colorScheme, textMeasurer)
                    drawYLabels(yAxis, colorScheme, textMeasurer)
                    drawXLabels(data.label, colorScheme, textMeasurer)
                }
                "pie" -> {
                    val minSize = with(density) {min(size.width, size.height).toDp()}
                    if(minSize < 20.dp) {
                        drawStripChart(data.list, data.label, colorScheme, colorList, textMeasurer)
                    } else {
                        drawPieChart(data.list, data.label, colorScheme, colorList, textMeasurer)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawYAxis(
    yAxis: List<Float>,
    colorScheme: ColorScheme
) {
    yAxis.forEachIndexed { index, _ ->
        val y = index * size.height / (yAxis.size - 1)
        drawLine(
            color = colorScheme.onSurface.copy(0.3f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawYLabels(
    yAxis: List<Float>,
    colorScheme: ColorScheme,
    textMeasurer: TextMeasurer // 传递 TextMeasurer
) {
    val fontSize = getFontSize(6f, 10f)
    val textStyle = TextStyle(fontSize = fontSize.sp)

    yAxis.forEachIndexed { index, value ->
        val y = index * size.height / (yAxis.size - 1)
        val textLayoutResult = textMeasurer.measure(value.toStringWithoutZeros(), textStyle)
        val textY = when (index) {
            0 -> y
            yAxis.size - 1 -> y - textLayoutResult.size.height
            else -> y - textLayoutResult.size.height / 2
        }
        drawStrokeText(
            textLayoutResult,
            color = colorScheme.onSurface.copy(.4f),
            strokeColor = colorScheme.surface,
            topLeft = Offset(0F, textY)
        )
    }
}

private fun DrawScope.drawXLabels(
    labels: List<String>,
    colorScheme: ColorScheme,
    textMeasurer: TextMeasurer // 传递 TextMeasurer
) {
    val fontSize = getFontSize(6f, 10f)
    val textStyle = TextStyle(fontSize = fontSize.sp)

    val canvasWidth = size.width
    val canvasHeight = size.height
    val labelWidth = canvasWidth / labels.size

    labels.forEachIndexed { index, label ->
        val x = index * labelWidth + labelWidth / 2
        val textLayoutResult = textMeasurer.measure(label, textStyle)
        drawStrokeText(
            textLayoutResult,
            color = colorScheme.onSurface.copy(.8f),
            strokeColor = colorScheme.surface,
            topLeft = Offset(x - textLayoutResult.size.width / 2, canvasHeight - textLayoutResult.size.height)
        )
    }
}

private fun DrawScope.drawBarChart(
    yAxis: List<Float>,
    data: List<Float>,
    colorScheme: ColorScheme,
    colorList: List<Color>,
    textMeasurer: TextMeasurer
) {
    val maxAxis = yAxis.max()
    val minAxis = yAxis.min()
    val canvasWidth = size.width
    val canvasHeight = size.height
    val xWidth = canvasWidth / data.size
    var barWidth = xWidth * 0.7f
    if(barWidth > 30 * density) {
        barWidth = 30 * density
    }
    val left = (xWidth - barWidth) / 2

    val fontSize = getFontSize()
    val textStyle = TextStyle(fontSize = fontSize.sp)

    // Draw Bars
    data.forEachIndexed { index, value ->
        val x = index * xWidth + left
        val v = if(value < 0) 0f else value
        val y = (maxAxis - v) / (maxAxis - minAxis) * canvasHeight
        val barHeight = value.absoluteValue / (maxAxis - minAxis) * canvasHeight
        drawRect(
            color = colorList[index],
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight)
        )

        if(value != 0f) {
            val textLayoutResult = textMeasurer.measure(value.toStringWithoutZeros(), textStyle)
            val textX = x + barWidth / 2 - textLayoutResult.size.width / 2
            var textY = if(value < 0) barHeight + y else y - textLayoutResult.size.height
            textY = when {
                textY < 0 -> 0f
                textY + textLayoutResult.size.height * 1.5 > canvasHeight -> canvasHeight - textLayoutResult.size.height * 1.5f
                else -> textY
            }

            drawStrokeText(
                textLayoutResult,
                color = colorScheme.onSurface.copy(.8f),
                strokeColor = colorScheme.surface,
                topLeft = Offset(textX, textY)
            )
        }
    }
}

private fun DrawScope.drawLineChart(
    yAxis: List<Float>,
    data: List<Float>,
    colorScheme: ColorScheme,
    textMeasurer: TextMeasurer
) {
    val maxAxis = yAxis.max()
    val minAxis = yAxis.min()
    val canvasWidth = size.width
    val canvasHeight = size.height
    val xWidth = canvasWidth / data.size
    val pointRadius = (size.width / density / 158 * 8f).coerceIn(3f, 10f)
    val strokeWidth = (size.width / density / 158 * 4f).coerceIn(1f, 5f)


    // Draw Line
    val points = data.mapIndexed { index, value ->
        Offset(
            index * xWidth + xWidth / 2,
            (maxAxis - value) / (maxAxis - minAxis) * canvasHeight
        )
    }

    val fontSize = getFontSize()
    val textStyle = TextStyle(fontSize = fontSize.sp)
    points.forEachIndexed { i, point ->
        if(i < points.size - 1) {
            drawLine(
                color = colorScheme.primary,
                start = points[i],
                end = points[i + 1],
                strokeWidth = strokeWidth
            )
        }

        drawCircle(
            color = colorScheme.primary,
            radius = pointRadius,
            center = point
        )

        if(data[i] != 0f) {
            val textLayoutResult = textMeasurer.measure(data[i].toStringWithoutZeros(), textStyle)
            val x = i * xWidth + xWidth / 2
            val y = (maxAxis - data[i]) / (maxAxis - minAxis) * canvasHeight
            val textHeight = textLayoutResult.size.height
            val leftValue = if(i == 0) data[i] else data[i - 1]
            val rightValue = if(i == data.size - 1) data[i] else data[i + 1]
            val midValue = (leftValue + rightValue) / 2
            val textSpace = textLayoutResult.size.height * 0.2f
            var textY = if(data[i] < midValue) y + textSpace else y - textHeight - textSpace
            textY = when {
                textY < 0 -> 0f
                textY + textLayoutResult.size.height * 1.5 > canvasHeight -> canvasHeight - textLayoutResult.size.height * 1.5f
                else -> textY
            }
            drawStrokeText(
                textLayoutResult,
                color = colorScheme.onSurface.copy(.8f),
                strokeColor = colorScheme.surface,
                topLeft = Offset(x - textLayoutResult.size.width / 2, textY)
            )
        }
    }
}

private fun DrawScope.drawPieChart(
    data: List<Float>,
    labels: List<String>,
    colorScheme: ColorScheme,
    colorList: List<Color>,
    textMeasurer: TextMeasurer // 传递 TextMeasurer
) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    val center = Offset(canvasWidth / 2, canvasHeight / 2)
    val total = data.sumOf{ it.absoluteValue.toDouble() }.toFloat()
    var startAngle = -90f

    val fontSize = getFontSize()
    val textStyle = TextStyle(fontSize = fontSize.sp)
    val labelSize = getFontSize(6f, 10f)
    val labelStyle = TextStyle(fontSize = labelSize.sp)

    // 获取label高度
    val labelLayoutResult = textMeasurer.measure(00f.toStringWithoutZeros(), labelStyle)
    val radius = min(canvasWidth, canvasHeight) / 2 - labelLayoutResult.size.height * 1.2f

    data.forEachIndexed { index, value ->
        val sweepAngle = if(total > 0) (value.absoluteValue / total) * 360f else 360f / data.size

        // 绘制扇形
        drawArc(
            color = if(total > 0) colorList[index] else colorScheme.onSurface.copy(.02f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2)
        )

        // 绘制value和标签
        val label = labels.getOrNull(index) ?: ""
        val angle = startAngle + sweepAngle / 2 // 扇形的中间角度
        val textX = center.x + cos(angle * PI / 180).toFloat() * (radius / 1.5f) // 数值位置在扇形内
        val textY = center.y + sin(angle * PI / 180).toFloat() * (radius / 1.5f)
        val labelX = center.x + cos(angle * PI / 180).toFloat() * radius // 标签位置在扇形外
        val labelY = center.y + sin(angle * PI / 180).toFloat() * radius

        val textLayoutResult = textMeasurer.measure(value.toStringWithoutZeros(), textStyle)
        drawStrokeText(
            textLayoutResult,
            color = colorScheme.onSurface.copy(.8f),
            strokeColor = colorScheme.surface,
            topLeft = Offset(textX - textLayoutResult.size.width / 2, textY - textLayoutResult.size.height / 2)
        )

        // 根据角度调整标签对齐方式
        val canvas = drawContext.canvas
        canvas.save()
        var labelAngle = angle + 90f
        if(15 < angle && angle < 165) {
            labelAngle += 180f
        }
        canvas.rotate(labelAngle, labelX, labelY)
        val labelLayoutResult = textMeasurer.measure(label, labelStyle)
        val labelLeft = if(15 < angle && angle < 165) labelX - labelLayoutResult.size.width / 2 else labelX - labelLayoutResult.size.width / 2
        val labelTop = if(15 < angle && angle < 165) labelY else labelY - labelLayoutResult.size.height
        drawStrokeText(
            labelLayoutResult,
            color = colorScheme.onSurface.copy(.8f),
            strokeColor = colorScheme.surface,
            topLeft = Offset(labelLeft, labelTop)
        )
        canvas.restore()

        startAngle += sweepAngle
    }
}

private fun DrawScope.drawStripChart(
    data: List<Float>,
    labels: List<String>,
    colorScheme: ColorScheme,
    colorList: List<Color>,
    textMeasurer: TextMeasurer // 传递 TextMeasurer
) {
    val isRow = size.width >= size.height
    val canvasWidth = size.width
    val canvasHeight = size.height
    val total = data.sumOf{ it.absoluteValue.toDouble() }.toFloat()
    val totalSize = if(isRow) size.width else size.height
    var x = 0f
    var y = 0f

    val fontSize = getFontSize()
    val textStyle = TextStyle(fontSize = fontSize.sp)
    val labelSize = getFontSize(6f, 10f)
    val labelStyle = TextStyle(fontSize = labelSize.sp)

    data.forEachIndexed { index, value ->
        val size = if(total > 0) (value.absoluteValue / total) * totalSize else totalSize / data.size
        val stripSize = if(isRow) Size(size, canvasHeight) else Size(canvasWidth, size)

        drawRect(
            color = if(total > 0) colorList[index] else colorScheme.onSurface.copy(.02f),
            topLeft = Offset(x, y),
            size = stripSize
        )

        if(value != 0f) {
            // 绘制value
            val textLayoutResult = textMeasurer.measure(value.toStringWithoutZeros(), textStyle)
            val textLeft = x + stripSize.width / 2 - textLayoutResult.size.width / 2
            val textTop = y + stripSize.height / 2 - textLayoutResult.size.height / 2

            drawStrokeText(
                textLayoutResult,
                color = colorScheme.onSurface.copy(.8f),
                strokeColor = colorScheme.surface,
                topLeft = Offset(textLeft, textTop)
            )

            // 绘制标签
            val label = labels.getOrNull(index) ?: ""
            if(label.isNotEmpty()) {
                val labelLayoutResult = textMeasurer.measure(label, labelStyle)
                val labelLeft = x + stripSize.width / 2 - labelLayoutResult.size.width / 2
                val labelTop = y + stripSize.height / 2 - labelLayoutResult.size.height / 2

                drawStrokeText(
                    labelLayoutResult,
                    color = colorScheme.onSurface.copy(.8f),
                    strokeColor = colorScheme.surface,
                    topLeft = if(isRow) { // 绘制到下边
                        Offset(labelLeft, labelTop + textLayoutResult.size.height)
                    } else { // 绘制到右边
                        Offset(labelLeft + textLayoutResult.size.width + 2, labelTop)
                    }
                )
            }
        }

        if(isRow) x += stripSize.width else y += stripSize.height
    }
}

private fun DrawScope.drawStrokeText(
    textLayoutResult: TextLayoutResult,
    color: Color = Color.Unspecified,
    strokeColor: Color = Color.Unspecified,
    topLeft: Offset = Offset.Zero,
    alpha: Float = Float.NaN
) {
    drawText(
        textLayoutResult,
        color = strokeColor,
        topLeft = topLeft,
        alpha = alpha,
        drawStyle = Stroke(1 * density)
    )
    drawText(
        textLayoutResult,
        color = color,
        topLeft = topLeft,
        alpha = alpha,
        drawStyle = Fill
    )
}

private fun DrawScope.getFontSize(
    normal: Float = 8f,
    maxSize: Float = 12f
): Float {
    return (max(size.width, size.height) / density / 158 * normal).coerceAtMost(maxSize)
}

private fun getYAxis(data: List<Float>): List<Float> {
    val maxValue = data.max()
    val minValue = data.min()
    val tempValue = if(maxValue > 0 && minValue > 0) {
        maxValue
    } else if(maxValue < 0 && minValue < 0) {
        -minValue
    } else if(maxValue == 0f && minValue == 0f) {
        1f
    } else {
        maxValue - minValue
    }

    val (value, pow) = getValuePow(tempValue)

    val steps = listOf(1, 2, 3, 5)
    val lines = listOf(4, 5, 6, 7, 8)
    val except = listOf(14, 16, 21, 24);

    var step = 1
    var line = 5
    outer@ for (s in steps) {
        for (l in lines) {
            if (s * l >= value) {
                step = s
                line = l
                if(!except.contains(s * l)) {
                    break@outer
                }
            }
        }
    }

    val list = mutableListOf<Float>()
    val stepValue = (10.0.pow(pow.toDouble()) * step).toFloat()
    //偏移
    var offset = 0f
    while(minValue < offset) {
        offset -= stepValue
    }

    for (i in line downTo 1) {
        list.add(stepValue * i + offset)
    }
    list.add(0f + offset)

    //修正
    while (list[0] < maxValue) {
        list.add(0, list[0] + stepValue)
    }

    return list
}

private fun getValuePow(value: Float): Pair<Float, Int> {
    var v = value
    var pow = 0
    while (v >= 40) {
        v /= 10
        pow++
    }
    while (v < 4 && v > 0) {
        v *= 10
        pow--
    }
    return Pair(v, pow)
}

fun parseChartData(str: String): ChartData {
    val data = ChartData()

    val parts = str.split("::")
    if (parts.size > 1) {
        val typeSize = parts[0].split("|")
        when {
            typeSize.size > 1 -> {
                data.type = typeSize[0]
                data.size = typeSize[1].toUIntOrNull() ?: data.size
            }
            typeSize[0] in listOf("bar", "line", "pie") -> {
                data.type = typeSize[0]
            }
            else -> {
                data.size = typeSize[0].toUIntOrNull() ?: data.size
            }
        }
    }

    val dataStr = if (parts.size > 1) parts[1] else str

    // 解析数据点和标签
    for (s in dataStr.split(",")) {
        val valueLabel = s.split("|")
        if (valueLabel.isNotEmpty()) {
            val value = valueLabel[0].toFloatOrNull() ?: 0f
            data.list.add(value)
            data.label.add(if (valueLabel.size > 1) valueLabel[1] else "")
        }
    }

    return data
}

fun mergeChartData(first: ChartData, second: ChartData): ChartData {
    val merged = ChartData(
        type = second.type,
        size = second.size,
        list = mutableListOf(),
        label = mutableListOf()
    )

    // 创建一个映射，用于存储第一个 ChartData 中标签的索引
    val labelMap = mutableMapOf<String, Int>()
    for ((index, label) in first.label.withIndex()) {
        if (label.isNotEmpty()) {
            labelMap[label] = index
        }
    }

    // 添加第一个 ChartData 的数据
    merged.list.addAll(first.list)
    merged.label.addAll(first.label)

    // 合并第二个 ChartData 的数据
    for ((index, label) in second.label.withIndex()) {
        if (label.isNotEmpty()) {
            if (labelMap.containsKey(label)) {
                // 如果标签已存在，更新对应的值
                val existingIndex = labelMap[label]!!
                merged.list[existingIndex] = second.list[index]
            } else {
                // 如果标签不存在，添加新的数据和标签
                merged.list.add(second.list[index])
                merged.label.add(label)
            }
        } else {
            // 如果标签为空，直接添加数据和标签
            merged.list.add(second.list[index])
            merged.label.add(label)
        }
    }

    // 如果合并后的数据超过 size，截取最后 size 个数据
    if (merged.list.size > merged.size.toInt()) {
        val start = merged.list.size - merged.size.toInt()
        merged.list = merged.list.subList(start, merged.list.size).toMutableList()
        merged.label = merged.label.subList(start, merged.label.size).toMutableList()
    }

    return merged
}

fun jsonChartData(json: String, context: Context): ChartData {
//    if (json.isBlank() || !json.trimStart().startsWith("{")) {
//        return ChartData()
//    }
    try {
        val chartData = Gson().fromJson(json, ChartData::class.java)
        return chartData
    } catch (e: Exception) {
        val log = Utils.getCrashLog("JsonChartData", e, context)
        sendPushMeLog("$log\n\njson:\n$json")
        return ChartData()
    }
}

fun Float.toStringWithoutZeros(): String {
    return if (this == this.toInt().toFloat()) {
        this.toInt().toString()
    } else {
        this.toString()
    }
}