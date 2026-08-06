# Moments release R8 — keep mínimo de app.
# Firebase / Media3 / Maps / Coil / kotlinx.serialization / CameraX traen consumer ProGuard.

# Stack traces legibles en crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Anotaciones / genéricos usados por serializers y APIs Google.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Lookups dinámicos de recursos → ver res/raw/keep.xml (tools:keep).

# ---------------------------------------------------------------------------
# Room / WorkManager — R8 fullMode quitaba `WorkDatabase_Impl.<init>()`,
# y Room lo instancia por reflexión → crash al arrancar (InitializationProvider).
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase {
    <init>();
    <methods>;
}
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); <methods>; }
-keep class androidx.work.impl.**_Impl { <init>(); <methods>; }
-keep class androidx.work.impl.model.**_Impl { <init>(); <methods>; }
-keep class androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory { <init>(); }
-keep class androidx.startup.** { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Compose + R8 horizontal class merging — StoryEditingView genera lambdas con
# ~90 params. R8 las fusionaba en clases ajenas (p.ej. Ktor PipelinePhaseRelation)
# y ART rechaza el dex: VerifyError (register Reference vs Integer esperado).
# Crash al abrir 1.0.2. Mantener el facade Kt evita el merge.
# ---------------------------------------------------------------------------
-keep class com.moments.android.views.creator.StoryeditorKt { *; }
-keep class com.moments.android.views.creator.HiddenLayersEditorViewKt { *; }
