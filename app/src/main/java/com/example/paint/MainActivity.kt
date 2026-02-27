package com.example.paint

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.applyCanvas
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.paint.ui.theme.PaintTheme
import kotlinx.coroutines.launch

// Definimos el color rojito claro para usarlo en toda la app
val RojoClaroApp = Color(0xFFFF8686)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaintTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "welcome") {
                    composable("welcome") { WelcomeScreen(navController) }
                    composable("instructions") { InstructionsScreen(navController) }
                    composable("paint") { PaintApp() }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bienvenido a Paint", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.size(24.dp))
        Button(
            onClick = { navController.navigate("instructions") },
            colors = ButtonDefaults.buttonColors(containerColor = RojoClaroApp)
        ) {
            Text("Instrucciones")
        }
        Spacer(modifier = Modifier.size(12.dp))
        Button(
            onClick = { navController.navigate("paint") },
            colors = ButtonDefaults.buttonColors(containerColor = RojoClaroApp)
        ) {
            Text("Dibujar")
        }
    }
}

@Composable
fun InstructionsScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Guía Rápida de Uso", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.size(16.dp))
        Text("""
            🎨 COLORES: Toca los círculos para cambiar el color del pincel.
            
            📏 TAMAÑO: Escribe el grosor en el recuadro gris.
            
            🧽 BORRAR: Pulsa 'Borrar' para usar el pincel blanco.
            
            ✏️ DIBUJAR: Pulsa 'Dibujar' para volver al color seleccionado.
            
            🧹 REINICIAR: El botón 'Reiniciar' limpia todo el lienzo.
            
            💾 GUARDAR: Pulsa 'Guardar' para exportar a la galería.
        """.trimIndent(), fontSize = 16.sp, lineHeight = 24.sp)

        Spacer(modifier = Modifier.size(32.dp))
        Button(
            onClick = { navController.navigate("paint") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = RojoClaroApp)
        ) {
            Text("Entendido! Ir a Dibujar")
        }
    }
}

@Composable
fun ColorPicker(onColorSelected: (Color) -> Unit) {
    val context = LocalContext.current
    val colorMap = mapOf(
        Color.Red to "Rojo",
        Color.Green to "Verde",
        Color.Blue to "Azul",
        Color.Black to "Negro"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colorMap.forEach { (color, name) ->
            Box(Modifier.size(35.dp)
                .background(color, CircleShape)
                .clickable {
                    onColorSelected(color)
                    Toast.makeText(context, name, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun BrushSizeSelector(currentSize: Float, onSizeSelected: (Float) -> Unit) {
    var sizeText by remember { mutableStateOf(currentSize.toString()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = sizeText,
            onValueChange = {
                sizeText = it
                onSizeSelected(it.toFloatOrNull() ?: currentSize)
            },
            textStyle = TextStyle(fontSize = 14.sp),
            modifier = Modifier.width(45.dp)
                .background(Color.LightGray, CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(" px", fontSize = 12.sp)
    }
}

data class Line(val start: Offset, val end: Offset, val color: Color, val strokeWidth: Float)

suspend fun saveDrawingToGallery(context: Context, lines: List<Line>) {
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        lines.forEach { line ->
            paint.color = line.color.toArgb()
            paint.strokeWidth = line.strokeWidth
            drawLine(line.start.x, line.start.y, line.end.x, line.end.y, paint)
        }
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "dibujo_${System.currentTimeMillis()}.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ThePaintApp")
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (uri != null) {
        resolver.openOutputStream(uri).use { outputStream ->
            outputStream?.let { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        Toast.makeText(context, "Guardado en Galería", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PaintApp() {
    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<Line>() }
    var currentColor by remember { mutableStateOf(Color.Black) }
    var brushSize by remember { mutableStateOf(10f) }
    var isEraser by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Barra de herramientas superior
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorPicker {
                currentColor = it
                isEraser = false
            }
            BrushSizeSelector(brushSize) { brushSize = it }

            Button(
                onClick = { isEraser = !isEraser },
                colors = ButtonDefaults.buttonColors(containerColor = RojoClaroApp)
            ) {
                Text(if (isEraser) "Dibujar" else "Borrar")
            }
        }

        // Botones de acción
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { lines.clear() },
                modifier = Modifier.padding(end = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RojoClaroApp)
            ) {
                Text("Reiniciar")
            }
            Button(
                onClick = { coroutine.launch { saveDrawingToGallery(context, lines) } },
                colors = ButtonDefaults.buttonColors(containerColor = RojoClaroApp)
            ) {
                Text("Guardar")
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val line = Line(
                        start = change.position - dragAmount,
                        end = change.position,
                        color = if (isEraser) Color.White else currentColor,
                        strokeWidth = brushSize
                    )
                    lines.add(line)
                }
            }
        ) {
            lines.forEach { line ->
                drawLine(
                    color = line.color,
                    start = line.start,
                    end = line.end,
                    strokeWidth = line.strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}