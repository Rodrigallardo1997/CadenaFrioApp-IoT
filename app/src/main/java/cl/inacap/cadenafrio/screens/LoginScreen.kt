package cl.inacap.cadenafrio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.cadenafrio.data.FirebaseRepository
import cl.inacap.cadenafrio.security.SeguridadIso27400
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    repo: FirebaseRepository,
    onLoginExitoso: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mostrarPassword by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var segundosBloqueo by remember { mutableLongStateOf(SeguridadIso27400.segundosRestantesBloqueo()) }

    // Contador para cuando se bloquea el login por fallar 3 veces
    LaunchedEffect(segundosBloqueo) {
        if (segundosBloqueo > 0L) {
            delay(1000L)
            segundosBloqueo = SeguridadIso27400.segundosRestantesBloqueo()
            if (segundosBloqueo == 0L) {
                mensajeError = "Tiempo de bloqueo terminado. Puedes volver a intentar."
            }
        }
    }

    val estaBloqueado = segundosBloqueo > 0L

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AcUnit,
                contentDescription = "Cadena de Frio",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Cadena de Frio IoT",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Monitoreo y Control de Almacenes Biomedicos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (!estaBloqueado) mensajeError = null
                },
                label = { Text("Correo institucional") },
                placeholder = { Text("ej. usuario@inacapmail.cl") },
                enabled = !estaBloqueado && !cargando,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (!estaBloqueado) mensajeError = null
                },
                label = { Text("Contrasena") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { mostrarPassword = !mostrarPassword }) {
                        Icon(
                            imageVector = if (mostrarPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Mostrar contrasena"
                        )
                    }
                },
                visualTransformation = if (mostrarPassword) VisualTransformation.None else PasswordVisualTransformation(),
                enabled = !estaBloqueado && !cargando,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (estaBloqueado) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = "ACCESO BLOQUEADO TEMPORALMENTE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Superaste los ${SeguridadIso27400.MAX_INTENTOS_LOGIN} intentos fallidos. Espera ${segundosBloqueo}s para volver a intentar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            } else if (mensajeError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mensajeError!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    cargando = true
                    repo.iniciarSesion(
                        email = email,
                        password = password,
                        onExito = {
                            cargando = false
                            onLoginExitoso()
                        },
                        onError = { err ->
                            cargando = false
                            mensajeError = err
                            segundosBloqueo = SeguridadIso27400.segundosRestantesBloqueo()
                        }
                    )
                },
                enabled = !cargando && !estaBloqueado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        estaBloqueado -> "Bloqueado (${segundosBloqueo}s)"
                        cargando -> "Iniciando sesion..."
                        else -> "Iniciar Sesion"
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Seguridad ISO 27400 - Control de acceso por roles",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
