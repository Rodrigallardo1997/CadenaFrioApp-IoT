package cl.inacap.cadenafrio

import cl.inacap.cadenafrio.model.EstadoCadenaFrio
import cl.inacap.cadenafrio.model.RolUsuario
import cl.inacap.cadenafrio.model.Telemetria
import cl.inacap.cadenafrio.security.SeguridadIso27400
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// Pruebas unitarias de la app Cadena de Frio
class CadenaFrioTest {

    @Before
    fun setUp() {
        SeguridadIso27400.reiniciarIntentosLogin()
    }

    @Test
    fun testTemperaturaNormal() {
        val estado1 = EstadoCadenaFrio.evaluar(2.0, false)
        val estado2 = EstadoCadenaFrio.evaluar(4.6, false)
        val estado3 = EstadoCadenaFrio.evaluar(7.9, false)

        assertEquals(EstadoCadenaFrio.Normal, estado1)
        assertEquals(EstadoCadenaFrio.Normal, estado2)
        assertEquals(EstadoCadenaFrio.Normal, estado3)
        assertFalse(estado2.esCritico)
    }

    @Test
    fun testAlertaCongelamiento() {
        val estado = EstadoCadenaFrio.evaluar(1.5, false)
        assertEquals(EstadoCadenaFrio.Congelamiento, estado)
        assertTrue(estado.esCritico)
    }

    @Test
    fun testAlertaSobretemperatura() {
        val estado = EstadoCadenaFrio.evaluar(8.0, false)
        assertEquals(EstadoCadenaFrio.Sobretemperatura, estado)
        assertTrue(estado.esCritico)
    }

    @Test
    fun testPuertaAbierta() {
        val estado = EstadoCadenaFrio.evaluar(5.0, true)
        assertEquals(EstadoCadenaFrio.PuertaAbierta, estado)
        assertFalse(estado.esCritico)
    }

    @Test
    fun testRangoSensorTmp36() {
        assertFalse(SeguridadIso27400.esTemperaturaPlausible(-45.0))
        assertFalse(SeguridadIso27400.esTemperaturaPlausible(130.0))
        assertTrue(SeguridadIso27400.esTemperaturaPlausible(4.5))
    }

    @Test
    fun testHashContrasenas() {
        val hashSup = "86f118230a4dff0ccc0478f44fa11cf88cbae3773f3c713985d0b3283a50057f"
        val hashOpe = "e79273be2769767aef3d3fac2a0216ea27228963c0be04dbfadd0d61e1a58d48"
        val hashTec = "16b0895fbcb9d2b79b667b3698204e508913e4b9f8ce46eb3c631bee533d011f"

        assertTrue(SeguridadIso27400.verificarPasswordHash("SupFrio2026!", "SALT_SUP_TI3042_2026", hashSup))
        assertTrue(SeguridadIso27400.verificarPasswordHash("OpeFrio2026!", "SALT_OPE_TI3042_2026", hashOpe))
        assertTrue(SeguridadIso27400.verificarPasswordHash("TecFrio2026!", "SALT_TEC_TI3042_2026", hashTec))
        assertFalse(SeguridadIso27400.verificarPasswordHash("123456", "SALT_SUP_TI3042_2026", hashSup))
    }

    @Test
    fun testBloqueoIntentosLogin() {
        SeguridadIso27400.registrarFalloCredenciales("Error")
        SeguridadIso27400.registrarFalloCredenciales("Error")
        assertEquals(0L, SeguridadIso27400.segundosRestantesBloqueo())

        SeguridadIso27400.registrarFalloCredenciales("Error")
        assertTrue(SeguridadIso27400.segundosRestantesBloqueo() > 0L)
    }

    @Test
    fun testPermisosPorRol() {
        assertTrue(SeguridadIso27400.tienePermisoComando(RolUsuario.SUPERVISOR, "CMD:SILENCIO"))
        assertFalse(SeguridadIso27400.tienePermisoComando(RolUsuario.TECNICO_IOT, "CMD:SILENCIO"))
        assertTrue(SeguridadIso27400.tienePermisoComando(RolUsuario.TECNICO_IOT, "CMD:TEST"))
        assertFalse(SeguridadIso27400.tienePermisoComando(RolUsuario.OPERADOR, "CMD:TEST"))
        assertFalse(SeguridadIso27400.tienePermisoComando(RolUsuario.SUPERVISOR, "CMD:OTRO"))
    }

    @Test
    fun testFirmaHmac() {
        val ahora = System.currentTimeMillis()
        val firma = SeguridadIso27400.generarFirmaIntegridad(4.6, false, ahora)
        val lectura = Telemetria(temperatura = 4.6, puertaAbierta = false, timestamp = ahora, firmaIntegridad = firma)
        val lecturaAlterada = lectura.copy(temperatura = 9.5)

        assertTrue(SeguridadIso27400.verificarFirmaIntegridad(lectura))
        assertFalse(SeguridadIso27400.verificarFirmaIntegridad(lecturaAlterada))
    }
}
