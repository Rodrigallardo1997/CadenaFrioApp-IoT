# CadenaFrioApp - Monitoreo de Cadena de Frio IoT

Aplicacion Android desarrollada en **Kotlin y Jetpack Compose** para la asignatura **Aplicaciones Moviles para IoT (TI3042) - INACAP**.

## Descripcion
Permite monitorear en tiempo real la temperatura (`2.0 °C` a `8.0 °C`) y el estado de la puerta de una camara de frio mediante **Wi-Fi (Firebase Realtime Database)** y **Bluetooth Clasico SPP (`HC-05` / `ESP32`)**.

## Pantallas principales
- `LoginScreen`: Inicio de sesion con validacion de credenciales, bloqueo tras 3 intentos fallidos y control de acceso por roles (`SUPERVISOR`, `TECNICO_IOT`, `OPERADOR`).
- `DashboardScreen`: Monitor de temperatura, compuerta y envio de comandos remotos (`Silenciar`, `Auto`, `Test`).
- `HistorialScreen`: Registro de lecturas recibidas desde Firebase.
- `BluetoothScreen`: Conexion local por Bluetooth SPP.
- `EmisorSimuladorScreen`: Modo emisor para pruebas entre dos dispositivos Android por Wi-Fi.
