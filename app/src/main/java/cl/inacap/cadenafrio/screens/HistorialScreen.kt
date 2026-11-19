package cl.inacap.cadenafrio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.inacap.cadenafrio.data.FirebaseRepository
import cl.inacap.cadenafrio.model.EstadoCadenaFrio
import cl.inacap.cadenafrio.model.EventoHistorial
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    repo: FirebaseRepository,
    onVolver: () -> Unit
) {
    var eventos by remember { mutableStateOf<List<EventoHistorial>>(emptyList()) }
    val formatoFecha = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    DisposableEffect(Unit) {
        val listener = repo.observarHistorial { lista ->
            eventos = lista
        }
        onDispose {
            repo.removerObservadorHistorial(listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Telemetria") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (eventos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text("No hay registros todavia.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(eventos, key = { it.id }) { item ->
                    val estadoEval = EstadoCadenaFrio.evaluar(item.temperatura, item.puertaAbierta)
                    val colorFondo = when (estadoEval) {
                        is EstadoCadenaFrio.Sobretemperatura,
                        is EstadoCadenaFrio.FallaSensor -> Color(0xFFA8123B)
                        is EstadoCadenaFrio.Congelamiento -> Color(0xFF2B6CB0)
                        is EstadoCadenaFrio.PuertaAbierta -> Color(0xFFF59E3B)
                        is EstadoCadenaFrio.Normal -> Color(0xFFB9E2BE)
                    }
                    val colorTexto = when (estadoEval) {
                        is EstadoCadenaFrio.Sobretemperatura,
                        is EstadoCadenaFrio.FallaSensor,
                        is EstadoCadenaFrio.Congelamiento -> Color.White
                        else -> Color(0xFF1A202C)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorFondo),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "${item.estado} - ${String.format("%.1f °C", item.temperatura)}",
                                fontWeight = FontWeight.Bold,
                                color = colorTexto
                            )
                            Text(
                                text = "Puerta: ${if (item.puertaAbierta) "Abierta" else "Cerrada"} | Origen: ${item.origen}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorTexto.copy(alpha = 0.9f)
                            )
                            Text(
                                text = "Fecha: ${formatoFecha.format(Date(item.timestamp))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorTexto.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
