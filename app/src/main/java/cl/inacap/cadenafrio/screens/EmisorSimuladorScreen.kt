package cl.inacap.cadenafrio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.inacap.cadenafrio.data.FirebaseRepository
import cl.inacap.cadenafrio.model.EstadoCadenaFrio

// Pantalla para usar un segundo celular como emisor de prueba por Wi-Fi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmisorSimuladorScreen(
    repo: FirebaseRepository,
    onVolver: () -> Unit
) {
    var temperatura by remember { mutableFloatStateOf(5.0f) }
    var puertaAbierta by remember { mutableStateOf(false) }
    var estadoEnvio by remember { mutableStateOf<String?>(null) }

    val evaluacion = EstadoCadenaFrio.evaluar(temperatura.toDouble(), puertaAbierta)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modo Emisor IoT") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Envia lecturas de prueba desde este dispositivo hacia Firebase para ver el cambio en el otro telefono.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Temperatura: ${String.format("%.1f °C", temperatura)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = temperatura,
                        onValueChange = { temperatura = it },
                        valueRange = -10f..20f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Puerta abierta")
                        Switch(
                            checked = puertaAbierta,
                            onCheckedChange = { puertaAbierta = it }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Estado calculado: ${evaluacion.etiqueta}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = {
                    val tempRedondeada = Math.round(temperatura * 10.0) / 10.0
                    repo.publicarTelemetria(
                        temp = tempRedondeada,
                        puertaAbierta = puertaAbierta,
                        origen = "Android-Emisor-WiFi"
                    ) { ok ->
                        estadoEnvio = if (ok) {
                            "Datos enviados por Wi-Fi ($tempRedondeada °C)"
                        } else {
                            "Error al enviar datos"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar Telemetria por Wi-Fi")
            }

            if (estadoEnvio != null) {
                Text(
                    text = estadoEnvio!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
