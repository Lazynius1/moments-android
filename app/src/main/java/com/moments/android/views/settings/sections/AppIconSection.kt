package com.moments.android.views.settings.sections

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import com.moments.android.views.shared.MomentsModalSheet
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Iconos de la app. Cada uno es un acceso del manifiesto (`.AppIcon<Nombre>`); solo uno está activo. */
enum class MomentsAppIcon(
    val aliasName: String,
    @StringRes val title: Int,
    @DrawableRes val preview: Int,
) {
    CLASSIC("Classic", R.string.app_icon_name_classic, R.drawable.app_icon_classic),
    NIGHT("Night", R.string.app_icon_name_night, R.drawable.app_icon_night),
    PURPLE("Purple", R.string.app_icon_name_purple, R.drawable.app_icon_purple),
    PURPLE_FLAT("PurpleFlat", R.string.app_icon_name_purple_flat, R.drawable.app_icon_purple_flat),
    WHITE_FLAT("WhiteFlat", R.string.app_icon_name_white_flat, R.drawable.app_icon_white_flat),
    GRAPE("Grape", R.string.app_icon_name_grape, R.drawable.app_icon_grape),
    INK("Ink", R.string.app_icon_name_ink, R.drawable.app_icon_ink),
    SILVER("Silver", R.string.app_icon_name_silver, R.drawable.app_icon_silver),
    GOLD("Gold", R.string.app_icon_name_gold, R.drawable.app_icon_gold),
    GLOW("Glow", R.string.app_icon_name_glow, R.drawable.app_icon_glow),
    OUTLINE("Outline", R.string.app_icon_name_outline, R.drawable.app_icon_outline),
    COSMIC("Cosmic", R.string.app_icon_name_cosmic, R.drawable.app_icon_cosmic),
    PIXEL("Pixel", R.string.app_icon_name_pixel, R.drawable.app_icon_pixel),
    PRIDE("Pride", R.string.app_icon_name_pride, R.drawable.app_icon_pride),
    PRIDE_FLAG("PrideFlag", R.string.app_icon_name_pride_flag, R.drawable.app_icon_pride_flag),
    PRIDE_RING("PrideRing", R.string.app_icon_name_pride_ring, R.drawable.app_icon_pride_ring),
    FIRE("Fire", R.string.app_icon_name_fire, R.drawable.app_icon_fire),
    CHROME("Chrome", R.string.app_icon_name_chrome, R.drawable.app_icon_chrome),
    NEON("Neon", R.string.app_icon_name_neon, R.drawable.app_icon_neon),
    SLIME("Slime", R.string.app_icon_name_slime, R.drawable.app_icon_slime),
    FLOWERS("Flowers", R.string.app_icon_name_flowers, R.drawable.app_icon_flowers),
    PLANET("Planet", R.string.app_icon_name_planet, R.drawable.app_icon_planet),
    ;

    fun component(context: Context) =
        ComponentName(context.packageName, "com.moments.android.AppIcon$aliasName")
}

object AppIconManager {
    fun current(context: Context): MomentsAppIcon {
        val pm = context.packageManager
        return MomentsAppIcon.entries.firstOrNull {
            it != MomentsAppIcon.CLASSIC &&
                pm.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: MomentsAppIcon.CLASSIC
    }

    /** Activa primero el nuevo acceso y luego apaga el resto, para que el lanzador nunca se quede sin ninguno. */
    fun apply(context: Context, icon: MomentsAppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            icon.component(context),
            if (icon == MomentsAppIcon.CLASSIC) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP,
        )
        MomentsAppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(
                it.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

private val BrandPurple = Color(0xFF5B2C6F)

/** Fila de Ajustes que abre el selector de iconos. */
@Composable
fun AppIconSection() {
    val context = LocalContext.current
    var currentIcon by remember { mutableStateOf(AppIconManager.current(context)) }
    var isShowingPicker by remember { mutableStateOf(false) }

    SettingsRow(
        icon = Icons.Outlined.AppShortcut,
        title = stringResource(R.string.settings_app_icon_title),
        subtitle = stringResource(currentIcon.title),
        onClick = { isShowingPicker = true },
    )

    if (isShowingPicker) {
        MomentsModalSheet(onDismissRequest = { isShowingPicker = false }, allowExpanded = false) { dismiss ->
            AppIconPicker(
                currentIcon = currentIcon,
                onApply = { icon ->
                    AppIconManager.apply(context, icon)
                    currentIcon = icon
                    dismiss()
                },
            )
        }
    }
}

/** Carrusel: el del centro se elige; los de los lados giran, se encogen y se desenfocan. */
@Composable
private fun AppIconPicker(
    currentIcon: MomentsAppIcon,
    onApply: (MomentsAppIcon) -> Unit,
) {
    val icons = MomentsAppIcon.entries
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val pagerState = rememberPagerState(initialPage = currentIcon.ordinal) { icons.size }
    val active = icons[pagerState.currentPage]
    val isActive = active == currentIcon
    val secondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.55f)

    // Como en iOS: el carrusel llena la hoja; nombre, contador y botón abajo.
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // El icono a la vista domina la hoja, como en iOS; los de los lados asoman por los bordes.
            val iconSize = minOf(maxHeight.value * 0.86f, maxWidth.value * 0.62f, 270f).dp
            val sidePadding = (maxWidth - iconSize) / 2
            val iconSizePx = with(LocalDensity.current) { iconSize.toPx() }
            val corner = RoundedCornerShape(iconSize * 0.225f)
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(iconSize),
                contentPadding = PaddingValues(horizontal = sidePadding),
                // Más alto que su hueco: los de los lados suben y bajan al girar sin cortarse en recto.
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredHeight(maxHeight + iconSize),
            ) { page ->
                val offset = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                val progress = min(abs(offset), 1f)
                Box(
                    Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            val scale = 1f - progress * 0.3f
                            scaleX = scale
                            scaleY = scale
                            // Como en iOS: se apartan hacia el borde y ese desplazamiento gira con el
                            // icono alrededor de su sitio, así el de la derecha sube y el de la izquierda baja.
                            val angle = -progress * iconSize.value / 2f
                            val radians = Math.toRadians(angle.toDouble())
                            val shift = offset * iconSizePx / 2f
                            translationX = shift * cos(radians).toFloat()
                            translationY = shift * sin(radians).toFloat()
                            rotationZ = angle
                        }
                        // Sin recorte: con el recorte cuadrado la sombra asomaba en picos por las esquinas.
                        .blur((progress * 4f).dp, BlurredEdgeTreatment.Unbounded)
                        .shadow(10.dp, corner)
                        .clip(corner),
                ) {
                    // La imagen trae el margen del icono adaptativo: se recorta a lo visible.
                    Image(
                        painter = painterResource(icons[page].preview),
                        contentDescription = stringResource(icons[page].title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                scaleX = 1.5f
                                scaleY = 1.5f
                            },
                    )
                }
            }
        }

        Box(
            Modifier
                .padding(top = 14.dp)
                .heightIn(min = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            icons.forEachIndexed { index, icon ->
                val offset = (index - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                Text(
                    stringResource(icon.title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        translationX = -offset * 150.dp.toPx()
                        alpha = (1f - abs(offset) * 2f).coerceIn(0f, 1f)
                    },
                )
            }
        }

        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(targetState = pagerState.currentPage, label = "appIconIndex") { page ->
                Text(
                    "%02d".format(page + 1),
                    color = BrandPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                " / %02d".format(icons.size),
                color = secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        Text(
            stringResource(R.string.app_icon_picker_description),
            color = secondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )

        Button(
            onClick = {
                HapticManager.shared.mediumImpact(view)
                onApply(active)
            },
            enabled = !isActive,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp),
        ) {
            Text(
                stringResource(if (isActive) R.string.app_icon_picker_active else R.string.app_icon_picker_activate),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
