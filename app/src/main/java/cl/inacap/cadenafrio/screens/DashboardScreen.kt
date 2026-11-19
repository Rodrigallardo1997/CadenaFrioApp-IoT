package cl.inacap.cadenafrio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.cadenafrio.data.FirebaseRepository
import cl.inacap.cadenafrio.model.EstadoCadenaFrio
import cl.inacap.cadenafrio.model.RolUsuario
import cl.inacap.cadenafrio.model.Telemetria
import cl.inacap.cadenafrio.security.SeguridadIso27400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repo: FirebaseRepository,
    onIrHistorial: () -> Unit,
    onIrEmisor: () -> Unit,
    onIrBluetooth: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    val context = LocalContext.current
    var telemetria by remember { mutableStateOf(Telemetria()) }
    var feedbackComando by remember { mutableStateOf<String?>(null) }
    var esErrorPermiso by remember { mutableStateOf(false) }
    var mostrarDialogoPassword by remember { mutableStateOf(false) }
    val conectadoRed = remember { SeguridadIso27400.hayConexionRed(context) }
    val rol = repo.rolActual()

    DisposableEffect(Unit) {
        val listener = repo.observarTelemetriaActual { nueva ->
            telemetria = nueva
        }
        onDispose {
            repo.removerObservadorActual(listener)
        }
    }

    val estadoActual = EstadoCadenaFrio.evaluar(telemetria.temperatura, telemetria.puertaAbierta)
    val integridadOk = SeguridadIso27400.verificarFirmaIntegridad(telemetria)
    val tramaReciente = SeguridadIso27400.esTramaReciente(telemetria.timestamp)

    val colorTarjeta = when (estadoActual) {
        is EstadoCadenaFrio.Sobretemperatura,
        is EstadoCadenaFrio.FallaSensor -> Color(0xFFA8123B)
        is EstadoCadenaFrio.Congelamiento -> Color(0xFF1E64B7)
        is EstadoCadenaFrio.PuertaAbierta -> Color(0xFFE68A2E)
        is EstadoCadenaFrio.Normal -> Color(0xFF1B7F3A)
    }
    val colorTexto = Color.White

    if (mostrarDialogoPassword) {
        DialogoCambiarPassword(
            emailUsuario = repo.usuarioActualEmail() ?: "",
            onDismiss = { mostrarDialogoPassword = false },
            onConfirmar = { actual, nueva, confirmar, onErrorModal ->
                repo.cambiarPasswordUsuarioActual(
                    passwordActual = actual,
                    nuevaPassword = nueva,
                    confirmarPassword = confirmar,
                    onExito = { msg ->
                        mostrarDialogoPassword = false
                        esErrorPermiso = false
                        feedbackComando = msg
                    },
                    onError = { err ->
                        onErrorModal(err)
                    }
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Monitor Cadena de Frio")
                        Text(
                            text = "Rol: ${rol.etiqueta}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarDialogoPassword = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Cambiar contrasena")
                    }
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesion")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onIrHistorial) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Historial")
                    }
                    TextButton(
                        onClick = {
                            if (rol.puedeUsarBluetooth) {
                                onIrBluetooth()
                            } else {
                                esErrorPermiso = true
                                feedbackComando = "El rol ${rol.etiqueta} no tiene acceso a Bluetooth."
                            }
                        }
                    ) {
                        Icon(
                            if (rol.puedeUsarBluetooth) Icons.Default.Bluetooth else Icons.Default.Lock,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Bluetooth")
                    }
                    TextButton(
                        onClick = {
                            if (rol.puedeUsarEmisor) {
                                onIrEmisor()
                            } else {
                                esErrorPermiso = true
                                feedbackComando = "El rol ${rol.etiqueta} no tiene acceso al Modo Emisor."
                            }
                        }
                    ) {
                        Icon(
                            if (rol.puedeUsarEmisor) Icons.Default.Sensors else Icons.Default.Lock,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Emisor")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Usuario: ${repo.usuarioActualEmail() ?: "Activo"} | Red Wi-Fi: ${if (conectadoRed) "Activa (TLS)" else "Sin red"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Tarjeta principal con el estado de la camara
            Card(
                colors = CardDefaults.cardColors(containerColor = colorTarjeta),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = estadoActual.etiqueta,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorTexto
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Origen: ${telemetria.origen} | Comando: ${telemetria.comandoActivo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorTexto
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (integridadOk && tramaReciente) {
                            "Firma HMAC-SHA256 verificada (${telemetria.firmaIntegridad.take(8)})"
                        } else {
                            "Firma HMAC-SHA256: lectura externa"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorTexto
                    )
                }
            }

            // Tarjetas de temperatura y puerta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Temperatura", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = String.format("%.1f °C", telemetria.temperatura),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Rango: 2.0 - 8.0 °C", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.DoorFront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Compuerta", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = if (telemetria.puertaAbierta) "ABIERTA" else "CERRADA",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (telemetria.puertaAbierta) Color(0xFFE65100) else Color(0xFF1B5E20)
                        )
                        Text("Sensor Reed / D2", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Botones para controlar los actuadores segun el rol
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Control Remoto de Actuadores",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(rol.etiqueta, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        repo.enviarComandoActuador("CMD:SILENCIO") { ok, msg ->
                            esErrorPermiso = !ok
                            feedbackComando = msg
                        }
                    },
                    enabled = rol.puedeSilenciarAlarma,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Silenciar")
                }
                OutlinedButton(
                    onClick = {
                        repo.enviarComandoActuador("CMD:AUTO") { ok, msg ->
                            esErrorPermiso = !ok
                            feedbackComando = msg
                        }
                    },
                    enabled = rol.puedeEjecutarTestYAuto,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Auto")
                }
                OutlinedButton(
                    onClick = {
                        repo.enviarComandoActuador("CMD:TEST") { ok, msg ->
                            esErrorPermiso = !ok
                            feedbackComando = msg
                        }
                    },
                    enabled = rol.puedeEjecutarTestYAuto,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test")
                }
            }

            if (rol == RolUsuario.OPERADOR) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = "Perfil OPERADOR (Solo lectura): Los comandos de actuadores, Bluetooth y Emisor estan deshabilitados.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (rol == RolUsuario.TECNICO_IOT) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Perfil TECNICO_IOT: Puedes ejecutar pruebas (Auto/Test), Bluetooth y Emisor. Silenciar alarma requiere rol SUPERVISOR.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (feedbackComando != null) {
                Text(
                    text = feedbackComando!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (esErrorPermiso) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DialogoCambiarPassword(
    emailUsuario: String,
    onDismiss: () -> Unit,
    onConfirmar: (actual: String, nueva: String, confirmar: String, onError: (String) -> Unit) -> Unit
) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var errorModal by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar Contrasena") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Cuenta: $emailUsuario",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = actual,
                    onValueChange = { actual = it; errorModal = null },
                    label = { Text("Contrasena actual") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = nueva,
                    onValueChange = { nueva = it; errorModal = null },
                    label = { Text("Nueva contrasena (min. 6)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmar,
                    onValueChange = { confirmar = it; errorModal = null },
                    label = { Text("Confirmar nueva contrasena") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (errorModal != null) {
                    Text(
                        text = errorModal!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmar(actual, nueva, confirmar) { err ->
                        errorModal = err
                    }
                }
            ) {
                Text("Actualizar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
