# Estilo Android de Ajustes y superficies secundarias

Este documento es el contrato visual y de interacción para `Settings`, sus
subsecciones, sheets y diálogos. La referencia funcional sigue siendo iOS, pero
la composición y navegación deben sentirse nativas de Android con Material 3.

## Principio

- Mantener la identidad de Moments: jerarquía, color, tipografía, iconografía y
  densidad visual.
- Sustituir glass, blur y transparencias de iOS por superficies sólidas y
  adaptativas.
- Usar componentes y comportamiento Material 3 cuando Android ya define el
  patrón: app bars, ripple, switches, sheets, diálogos, insets e IME.
- No adoptar por ahora la API experimental `MaterialExpressiveTheme`; el tema
  estable del proyecto continúa siendo la fuente de verdad.

## Ventana edge-to-edge

`edge-to-edge` describe la ventana bajo las barras del sistema, no tarjetas
pegadas físicamente a los laterales.

- `MainActivity` llama a `enableEdgeToEdge()`.
- El canvas se dibuja detrás de status/navigation bar y ambas usan iconos con
  contraste correcto.
- `MainActivity` usa `android:windowSoftInputMode="adjustResize"`.
- Cada `Scaffold` aplica `innerPadding` una sola vez y después
  `consumeWindowInsets(innerPadding)`.
- No añadir `statusBarsPadding()` dentro de una pantalla que ya vive bajo un
  `Scaffold` con app bar.
- Una pantalla con inputs usa `imePadding()` en el contenedor que debe quedar
  visible, no en cada campo.

Canvas de Ajustes:

- oscuro: `#0B1215`
- claro: `#FAF9F6`

Las barras del sistema comparten ese canvas. La barra inferior puede mantener
un negro sólido o una variante ligeramente más oscura cuando mejore el
contraste; nunca debe quedar transparente mostrando contenido en movimiento.

## Shell de subsección

Toda ruta abierta desde Ajustes usa `SettingsSubsectionWrapper`:

- `Scaffold` Material 3.
- `CenterAlignedTopAppBar` sólida y del mismo color que el canvas.
- flecha Back visible con objetivo táctil mínimo Material.
- `BackHandler` equivalente a la flecha.
- el contenido recibe el área ya corregida por la app bar y barras del sistema.

No presentar una subsección normal dentro de un `Dialog` fullscreen. Debe ser
una ruta en la misma superficie de Ajustes. Un visor realmente inmersivo sí
puede usar un diálogo fullscreen, con
`DialogProperties(decorFitsSystemWindows = false)`.

## Cajas y retícula

Las cajas son parte del lenguaje visual aprobado de Moments.

- margen exterior horizontal: `8.dp`
- radio de grupo: `20.dp`
- superficie: `SettingsProfileColors.surfaceContainer(isDark)`
- elevación tonal y sombra: `0.dp`
- espacio vertical entre grupos: `24.dp`
- padding interno horizontal de fila: `16.dp`
- alto mínimo de fila: `64.dp`
- slot de icono: `28.dp`
- separación icono/texto: `14.dp`
- tamaño visual habitual del icono: `19.dp`
- inicio del divisor: `58.dp`
- etiqueta de sección: `labelMedium`, mayúsculas y `8.dp` antes de la caja

Componentes fuente:

- `SettingsGroup`: grupos de la pantalla raíz.
- `SettingsSubsectionGroup`: grupos con margen exterior para subsecciones.
- `SettingsSectionCard`: superficie sin etiqueta para composiciones especiales.
- `SettingsRow` y `SettingsToggleRow`: filas alineadas y con semántica.

Una advertencia puede conservar borde o acento semántico, pero debe mantener el
radio, ancho y superficie base del resto de Ajustes. Evitar mezclar tarjetas de
12, 16 y 20 dp dentro de la misma pantalla.

Ejemplo:

```kotlin
SettingsSubsectionGroup(title = stringResource(R.string.section_title)) {
    SettingsToggleRow(...)
    HorizontalDivider(Modifier.padding(start = SettingsDividerStart))
    SettingsRow(...)
}
```

## Bottom sheets

Todos los sheets de Ajustes usan `MomentsModalSheet`.

- `largeOnly = false`: sheet mediano ampliable.
- `largeOnly = true`: flujo complejo, selector largo o contenido que necesita
  altura estable.
- la altura del contenido permanece estable durante el gesto; los anclajes y el
  desplazamiento los controla `ModalBottomSheet`. No animar la altura en función
  de `sheetState.targetValue`, porque compite con el dedo.
- radio superior: `28.dp`.
- superficie sólida adaptativa; sin glass/blur.
- el `drag handle` es la única pista visual de cierre por gesto. No añadir un
  chevron hacia abajo en el header para duplicarlo. Un chevron se reserva para
  desplegables; una flecha izquierda, para volver a un nivel interno real.
- el header empieza inmediatamente después del handle, con `4–8.dp` extra; no
  se reutiliza además el padding superior de una pantalla fullscreen.
- el host resuelve navigation bar e IME mediante `navigationBarsPadding()` e
  `imePadding()`.
- el contenido no vuelve a aplicar esos insets.
- padding lateral recomendado del contenido: `20.dp`; `24.dp` cuando la
  composición sea principalmente textual. Las listas edge-to-edge dentro del
  sheet pueden usar filas con `16.dp`, pero no texto pegado al borde.
- objetivo táctil mínimo: `48.dp`.
- título y acción principal permanecen visibles o alcanzables al abrir teclado.

El lambda del sheet entrega `dismiss`:

```kotlin
MomentsModalSheet(
    onDismissRequest = { showSheet = false },
    largeOnly = false,
) { dismiss ->
    SheetContent(onClose = dismiss)
}
```

Los botones internos deben llamar a `dismiss()`. No deben cambiar directamente
el booleano del host, porque eso elimina el contenido antes de completar la
animación Material de cierre. Si hay procesamiento irreversible,
`dismissEnabled = false` hasta terminar.

## Back y jerarquía

Back siempre deshace el nivel más interno:

1. cierra selección, menú o modo edición;
2. cierra el sheet con su animación;
3. vuelve de la subsección a Ajustes;
4. vuelve de Ajustes a Perfil.

La flecha de la app bar y el gesto/botón Back ejecutan la misma función. Una
subruta de `Tu actividad` debe volver primero a `Tu actividad`, no saltar a
Ajustes ni cerrar la pantalla completa.

## Estados y controles

- Usar ripple/indication Material en filas; las animaciones propias de Moments
  pueden acompañarlo, no sustituir la respuesta táctil accesible.
- Todos los buscadores de Ajustes y flujos relacionados usan
  `SettingsSearchField`: alto mínimo `48.dp`, radio `24.dp`, superficie
  `surfaceContainer`, borde `outlineVariant`, texto `onSurface` y placeholder /
  iconos `onSurfaceVariant`. No usar `momentsChromeGlass` ni un `TextField`
  transparente sobre el canvas para búsquedas.
- Switch: la fila completa es pulsable con `Role.Switch`; el `Switch` visual no
  duplica el callback.
- Loading bloquea solamente la acción afectada.
- Error recuperable: diálogo Material 3 con título, explicación y acción.
- Acción destructiva: color semántico y confirmación cuando no sea reversible.
- Texto secundario usa `onSurfaceVariant`; divisores usan `outlineVariant`.

## Checklist por pantalla

- [ ] App bar sólida, título centrado y Back correcto.
- [ ] Status bar y canvas coinciden en claro y oscuro.
- [ ] Navigation bar no revela contenido o transparencia accidental.
- [ ] Ningún contenido entra en status/navigation bar.
- [ ] Grupos a `8.dp`, radio `20.dp`, retícula de filas alineada.
- [ ] Scroll llega al último control sin quedar bajo barras del sistema.
- [ ] IME no tapa el campo ni la acción principal.
- [ ] Back cierra primero el estado más interno.
- [ ] Sheet usa `MomentsModalSheet` y su callback `dismiss`.
- [ ] Sheet funciona en estado medio, expandido y con teclado.
- [ ] TalkBack recibe roles y descripciones útiles.
- [ ] Verificado en tema claro/oscuro y navegación por gestos/3 botones.
