package com.moments.android.reportes

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.models.Story

/** Port de ReportCategory + ReportPriority en ReportBottomSheet.swift */
enum class ReportCategory(val raw: String) {
    DISLIKE("no_me_gusta"),
    BULLYING("bullying_contacto_no_deseado"),
    SELF_HARM("suicidio_autolesion_trastornos"),
    VIOLENCE("violencia_odio_explotacion"),
    RESTRICTED_SALES("venta_articulos_restringidos"),
    NUDITY("desnudos_actividad_sexual"),
    SCAMS("estafas_fraudes_spam"),
    FALSE_INFO("informacion_falsa"),
    INTELLECTUAL_PROPERTY("propiedad_intelectual"),
    ;

    @get:StringRes
    val titleRes: Int
        get() = when (this) {
            DISLIKE -> R.string.report_category_dislike_title
            BULLYING -> R.string.report_category_bullying_title
            SELF_HARM -> R.string.report_category_selfHarm_title
            VIOLENCE -> R.string.report_category_violence_title
            RESTRICTED_SALES -> R.string.report_category_restrictedSales_title
            NUDITY -> R.string.report_category_nudity_title
            SCAMS -> R.string.report_category_scams_title
            FALSE_INFO -> R.string.report_category_falseInfo_title
            INTELLECTUAL_PROPERTY -> R.string.report_category_intellectualProperty_title
        }

    @get:StringRes
    val subtitleRes: Int
        get() = when (this) {
            DISLIKE -> R.string.report_category_dislike_subtitle
            BULLYING -> R.string.report_category_bullying_subtitle
            SELF_HARM -> R.string.report_category_selfHarm_subtitle
            VIOLENCE -> R.string.report_category_violence_subtitle
            RESTRICTED_SALES -> R.string.report_category_restrictedSales_subtitle
            NUDITY -> R.string.report_category_nudity_subtitle
            SCAMS -> R.string.report_category_scams_subtitle
            FALSE_INFO -> R.string.report_category_falseInfo_subtitle
            INTELLECTUAL_PROPERTY -> R.string.report_category_intellectualProperty_subtitle
        }

    val icon: ImageVector
        get() = when (this) {
            DISLIKE -> Icons.Default.ThumbDown
            BULLYING -> Icons.Default.Block
            SELF_HARM -> Icons.Default.FavoriteBorder
            VIOLENCE -> Icons.Default.Warning
            RESTRICTED_SALES -> Icons.Default.ShoppingCart
            NUDITY -> Icons.Default.VisibilityOff
            SCAMS -> Icons.Default.Error
            FALSE_INFO -> Icons.Default.Info
            INTELLECTUAL_PROPERTY -> Icons.Default.Copyright
        }

    val priority: ReportPriority
        get() = when (this) {
            DISLIKE -> ReportPriority.LOW
            BULLYING, SCAMS, FALSE_INFO, INTELLECTUAL_PROPERTY -> ReportPriority.MEDIUM
            SELF_HARM, VIOLENCE, NUDITY, RESTRICTED_SALES -> ReportPriority.HIGH
        }

    companion object {
        val allCases: List<ReportCategory> = entries
    }
}

enum class ReportPriority(val raw: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical"),
}

/** Port de UserReportReason en UserReportContent.swift */
enum class UserReportReason(val raw: String) {
    INAPPROPRIATE_CONTENT("account_inappropriate_content"),
    IMPERSONATION("account_impersonation"),
    UNDERAGE("account_underage"),
    BULLYING("account_bullying_contact"),
    SCAM("account_scam_spam"),
    ;

    @get:StringRes
    val titleRes: Int
        get() = when (this) {
            INAPPROPRIATE_CONTENT -> R.string.report_user_reason_inappropriate_title
            IMPERSONATION -> R.string.report_user_reason_impersonation_title
            UNDERAGE -> R.string.report_user_reason_underage_title
            BULLYING -> R.string.report_user_reason_bullying_title
            SCAM -> R.string.report_user_reason_scam_title
        }

    @get:StringRes
    val subtitleRes: Int
        get() = when (this) {
            INAPPROPRIATE_CONTENT -> R.string.report_user_reason_inappropriate_subtitle
            IMPERSONATION -> R.string.report_user_reason_impersonation_subtitle
            UNDERAGE -> R.string.report_user_reason_underage_subtitle
            BULLYING -> R.string.report_user_reason_bullying_subtitle
            SCAM -> R.string.report_user_reason_scam_subtitle
        }

    val icon: ImageVector
        get() = when (this) {
            INAPPROPRIATE_CONTENT -> Icons.Default.Report
            IMPERSONATION -> Icons.Default.Person
            UNDERAGE -> Icons.Default.ChildCare
            BULLYING -> Icons.Default.Gavel
            SCAM -> Icons.Default.Shield
        }

    val priority: ReportPriority
        get() = when (this) {
            UNDERAGE -> ReportPriority.HIGH
            IMPERSONATION, BULLYING, SCAM -> ReportPriority.MEDIUM
            INAPPROPRIATE_CONTENT -> ReportPriority.LOW
        }

    companion object {
        val allCases: List<UserReportReason> = entries
    }
}

/** Port de ReportBottomSheet.swift — contenedor universal de reporte. */
sealed class ReportTarget {
    data class MomentTarget(val moment: Moment) : ReportTarget()
    data class StoryTarget(val story: Story) : ReportTarget()
    data class UserTarget(val userId: String, val username: String? = null) : ReportTarget()
}
