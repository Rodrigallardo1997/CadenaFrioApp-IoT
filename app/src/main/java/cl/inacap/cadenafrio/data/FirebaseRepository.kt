package cl.inacap.cadenafrio.data

import cl.inacap.cadenafrio.model.EstadoCadenaFrio
import cl.inacap.cadenafrio.model.EventoHistorial
import cl.inacap.cadenafrio.model.RolUsuario
import cl.inacap.cadenafrio.model.Telemetria
import cl.inacap.cadenafrio.security.SeguridadIso27400
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// Datos de cada usuario registrado (guardamos el salt y el hash, no la clave en texto)
data class CuentaRegistrada(
    val email: String,
    val salt: String,
    var passwordHash: String,
    val rol: RolUsuario
)

// Clase para manejar la conexion con Firebase Auth y Realtime Database
class FirebaseRepository {

    private var auth: FirebaseAuth? = null
    private var db: FirebaseDatabase? = null

    init {
        try {
            auth = FirebaseAuth.getInstance()
            db = FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            auth = null
            db = null
        }
    }

    private val refActual = db?.getReference("cadena_frio/actual")
    private val refComando = db?.getReference("cadena_frio/actual/comandoActivo")
    private val refHistorial = db?.getReference("cadena_frio/historial")
    private val refUsuarios = db?.getReference("cadena_frio/usuarios")

    // Cuentas de los 3 perfiles con sus hashes PBKDF2
    private val cuentasSistema = mutableMapOf(
        "supervisor@inacapmail.cl" to CuentaRegistrada(
            email = "supervisor@inacapmail.cl",
            salt = "SALT_SUP_TI3042_2026",
            passwordHash = "86f118230a4dff0ccc0478f44fa11cf88cbae3773f3c713985d0b3283a50057f",
            rol = RolUsuario.SUPERVISOR
        ),
        "operador@inacapmail.cl" to CuentaRegistrada(
            email = "operador@inacapmail.cl",
            salt = "SALT_OPE_TI3042_2026",
            passwordHash = "e79273be2769767aef3d3fac2a0216ea27228963c0be04dbfadd0d61e1a58d48",
            rol = RolUsuario.OPERADOR
        ),
        "tecnico@inacapmail.cl" to CuentaRegistrada(
            email = "tecnico@inacapmail.cl",
            salt = "SALT_TEC_TI3042_2026",
            passwordHash = "16b0895fbcb9d2b79b667b3698204e508913e4b9f8ce46eb3c631bee533d011f",
            rol = RolUsuario.TECNICO_IOT
        )
    )

    private var emailSesionLocal: String? = null
    private var rolSesionActual: RolUsuario = RolUsuario.OPERADOR

    private val tsInicial = System.currentTimeMillis()
    private var telemetriaLocal = Telemetria(
        temperatura = 4.6,
        puertaAbierta = false,
        estado = EstadoCadenaFrio.Normal.etiqueta,
        comandoActivo = "AUTO",
        origen = "NODO_ARDUINO_WIFI",
        timestamp = tsInicial,
        firmaIntegridad = SeguridadIso27400.generarFirmaIntegridad(4.6, false, tsInicial),
        ultimoOperadorComando = "supervisor@inacapmail.cl (SUPERVISOR)"
    )

    private val historialLocal = mutableListOf(
        EventoHistorial(
            id = "evt-1",
            temperatura = 4.6,
            puertaAbierta = false,
            estado = EstadoCadenaFrio.Normal.etiqueta,
            origen = "NODO_ARDUINO_WIFI",
            timestamp = System.currentTimeMillis() - 180000L
        ),
        EventoHistorial(
            id = "evt-2",
            temperatura = 8.4,
            puertaAbierta = true,
            estado = EstadoCadenaFrio.Sobretemperatura.etiqueta,
            origen = "NODO_ARDUINO_WIFI",
            timestamp = System.currentTimeMillis() - 600000L
        ),
        EventoHistorial(
            id = "evt-3",
            temperatura = 1.5,
            puertaAbierta = false,
            estado = EstadoCadenaFrio.Congelamiento.etiqueta,
            origen = "NODO_ARDUINO_WIFI",
            timestamp = System.currentTimeMillis() - 1200000L
        )
    )

    private val observadoresActualLocal = mutableListOf<(Telemetria) -> Unit>()
    private val observadoresHistorialLocal = mutableListOf<(List<EventoHistorial>) -> Unit>()

    fun usuarioActualEmail(): String? = emailSesionLocal ?: auth?.currentUser?.email

    fun rolActual(): RolUsuario = rolSesionActual

    fun estaAutenticado(): Boolean = emailSesionLocal != null

    fun cerrarSesion() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            // Ignorar si no habia sesion en Firebase
        }
        emailSesionLocal = null
    }

    fun iniciarSesion(
        email: String,
        password: String,
        onExito: (RolUsuario) -> Unit,
        onError: (String) -> Unit
    ) {
        val errorFormato = SeguridadIso27400.validarFormatoCredenciales(email, password)
        if (errorFormato != null) {
            onError(errorFormato)
            return
        }

        val correoLimpio = email.trim().lowercase()
        val cuentaLocal = cuentasSistema[correoLimpio]

        if (cuentaLocal != null) {
            val coincideHash = SeguridadIso27400.verificarPasswordHash(
                passwordIngresada = password,
                salt = cuentaLocal.salt,
                hashEsperado = cuentaLocal.passwordHash
            )
            if (!coincideHash) {
                val msgFallo = SeguridadIso27400.registrarFalloCredenciales(
                    "Contrasena incorrecta para ${cuentaLocal.email}"
                )
                onError(msgFallo)
                return
            }

            SeguridadIso27400.reiniciarIntentosLogin()
            emailSesionLocal = cuentaLocal.email
            rolSesionActual = cuentaLocal.rol

            try {
                auth?.signInWithEmailAndPassword(correoLimpio, password)?.addOnSuccessListener { res ->
                    val uid = res.user?.uid
                    if (uid != null) {
                        sincronizarRolEnFirebase(uid, correoLimpio, cuentaLocal.rol)
                    }
                }
            } catch (e: Exception) {
                // Continua con la sesion validada
            }
            onExito(cuentaLocal.rol)
            return
        }

        val firebaseAuth = auth
        if (firebaseAuth == null) {
            val msgFallo = SeguridadIso27400.registrarFalloCredenciales("Usuario no registrado")
            onError(msgFallo)
            return
        }

        firebaseAuth.signInWithEmailAndPassword(correoLimpio, password)
            .addOnSuccessListener { res ->
                SeguridadIso27400.reiniciarIntentosLogin()
                val rolInferido = RolUsuario.inferirPorEmail(correoLimpio)
                emailSesionLocal = correoLimpio
                rolSesionActual = rolInferido
                val uid = res.user?.uid
                if (uid != null) {
                    sincronizarRolEnFirebase(uid, correoLimpio, rolInferido)
                }
                onExito(rolInferido)
            }
            .addOnFailureListener {
                val msgFallo = SeguridadIso27400.registrarFalloCredenciales(
                    "Correo no registrado o contrasena invalida"
                )
                onError(msgFallo)
            }
    }

    fun cambiarPasswordUsuarioActual(
        passwordActual: String,
        nuevaPassword: String,
        confirmarPassword: String,
        onExito: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val correo = usuarioActualEmail()?.lowercase()
        if (correo == null) {
            onError("No hay una sesion activa.")
            return
        }
        val errorNueva = SeguridadIso27400.validarNuevaPassword(nuevaPassword, confirmarPassword)
        if (errorNueva != null) {
            onError(errorNueva)
            return
        }

        val cuenta = cuentasSistema[correo]
        if (cuenta != null) {
            val actualCorrecta = SeguridadIso27400.verificarPasswordHash(
                passwordIngresada = passwordActual,
                salt = cuenta.salt,
                hashEsperado = cuenta.passwordHash
            )
            if (!actualCorrecta) {
                onError("La contrasena actual no es correcta.")
                return
            }
            if (passwordActual == nuevaPassword) {
                onError("La nueva contrasena debe ser distinta a la actual.")
                return
            }
            cuenta.passwordHash = SeguridadIso27400.generarHashPassword(nuevaPassword, cuenta.salt)
        }

        try {
            auth?.currentUser?.updatePassword(nuevaPassword)
        } catch (e: Exception) {
            // Se mantiene actualizado el hash local
        }
        onExito("Contrasena actualizada correctamente para $correo.")
    }

    private fun sincronizarRolEnFirebase(uid: String, email: String, rol: RolUsuario) {
        try {
            val datosPerfil = mapOf(
                "email" to email,
                "rol" to rol.name,
                "actualizadoEn" to System.currentTimeMillis()
            )
            refUsuarios?.child(uid)?.setValue(datosPerfil)
        } catch (e: Exception) {
            // Error de red al guardar perfil
        }
    }

    fun observarTelemetriaActual(onCambio: (Telemetria) -> Unit): ValueEventListener {
        observadoresActualLocal.add(onCambio)
        onCambio(telemetriaLocal)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val telemetria = snapshot.getValue(Telemetria::class.java)
                if (telemetria != null && SeguridadIso27400.esTemperaturaPlausible(telemetria.temperatura)) {
                    telemetriaLocal = telemetria
                    onCambio(telemetria)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        try {
            refActual?.addValueEventListener(listener)
        } catch (e: Exception) {}
        return listener
    }

    fun removerObservadorActual(listener: ValueEventListener) {
        try {
            refActual?.removeEventListener(listener)
        } catch (e: Exception) {}
    }

    fun observarHistorial(onCambio: (List<EventoHistorial>) -> Unit): ValueEventListener {
        observadoresHistorialLocal.add(onCambio)
        onCambio(historialLocal.sortedByDescending { it.timestamp })

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<EventoHistorial>()
                for (hijo in snapshot.children) {
                    val item = hijo.getValue(EventoHistorial::class.java)
                    if (item != null) {
                        lista.add(item.copy(id = hijo.key ?: ""))
                    }
                }
                if (lista.isNotEmpty()) {
                    onCambio(lista.sortedByDescending { it.timestamp })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        try {
            refHistorial?.limitToLast(30)?.addValueEventListener(listener)
        } catch (e: Exception) {}
        return listener
    }

    fun removerObservadorHistorial(listener: ValueEventListener) {
        try {
            refHistorial?.removeEventListener(listener)
        } catch (e: Exception) {}
    }

    fun enviarComandoActuador(comando: String, onResultado: (Boolean, String) -> Unit) {
        if (!estaAutenticado()) {
            onResultado(false, "Debes iniciar sesion primero.")
            return
        }
        if (!SeguridadIso27400.esComandoValido(comando)) {
            onResultado(false, "Comando no permitido.")
            return
        }
        if (!SeguridadIso27400.tienePermisoComando(rolSesionActual, comando)) {
            onResultado(
                false,
                "El rol ${rolSesionActual.etiqueta} no tiene permiso para enviar $comando."
            )
            return
        }
        val errorRateLimit = SeguridadIso27400.verificarRateLimitComando()
        if (errorRateLimit != null) {
            onResultado(false, errorRateLimit)
            return
        }

        val ahora = System.currentTimeMillis()
        val autor = "${usuarioActualEmail() ?: "Usuario"} (${rolSesionActual.etiqueta})"
        val nuevaFirma = SeguridadIso27400.generarFirmaIntegridad(
            telemetriaLocal.temperatura,
            telemetriaLocal.puertaAbierta,
            ahora
        )

        telemetriaLocal = telemetriaLocal.copy(
            comandoActivo = comando,
            timestamp = ahora,
            firmaIntegridad = nuevaFirma,
            ultimoOperadorComando = autor
        )
        for (obs in observadoresActualLocal) {
            obs(telemetriaLocal)
        }
        onResultado(true, "Comando $comando enviado (${rolSesionActual.etiqueta})")

        try {
            refComando?.setValue(comando)
            refActual?.child("ultimoOperadorComando")?.setValue(autor)
        } catch (e: Exception) {}
    }

    fun publicarTelemetria(
        temp: Double,
        puertaAbierta: Boolean,
        origen: String,
        onResultado: (Boolean) -> Unit
    ) {
        if (!estaAutenticado() || !SeguridadIso27400.tienePermisoPublicarTelemetria(rolSesionActual)) {
            onResultado(false)
            return
        }
        if (!SeguridadIso27400.esTemperaturaPlausible(temp)) {
            onResultado(false)
            return
        }

        val ahora = System.currentTimeMillis()
        val estadoEval = EstadoCadenaFrio.evaluar(temp, puertaAbierta)
        val firma = SeguridadIso27400.generarFirmaIntegridad(temp, puertaAbierta, ahora)

        val nuevaTelemetria = Telemetria(
            temperatura = temp,
            puertaAbierta = puertaAbierta,
            estado = estadoEval.etiqueta,
            comandoActivo = telemetriaLocal.comandoActivo,
            origen = "$origen (${rolSesionActual.etiqueta})",
            timestamp = ahora,
            firmaIntegridad = firma,
            ultimoOperadorComando = telemetriaLocal.ultimoOperadorComando
        )

        telemetriaLocal = nuevaTelemetria
        val eventoLocal = EventoHistorial(
            id = "evt-$ahora",
            temperatura = temp,
            puertaAbierta = puertaAbierta,
            estado = estadoEval.etiqueta,
            origen = "$origen (${rolSesionActual.etiqueta})",
            timestamp = ahora
        )
        historialLocal.add(0, eventoLocal)
        for (obs in observadoresActualLocal) {
            obs(telemetriaLocal)
        }
        for (obs in observadoresHistorialLocal) {
            obs(historialLocal.sortedByDescending { ev -> ev.timestamp })
        }
        onResultado(true)

        try {
            refActual?.setValue(nuevaTelemetria)?.addOnSuccessListener {
                refHistorial?.push()?.setValue(eventoLocal)
            }
        } catch (e: Exception) {}
    }
}
