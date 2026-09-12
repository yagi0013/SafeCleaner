package jp.yagi.safecleaner.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jp.yagi.safecleaner.model.InstalledApp
import jp.yagi.safecleaner.model.TargetPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.exclusionDataStore by preferencesDataStore("exclusions")

class ExclusionRepository(private val context: Context) {
    private val excludedPackagesKey = stringSetPreferencesKey("excluded_packages")

    val userExcludedPackages: Flow<Set<String>> = context.exclusionDataStore.data.map {
        it[excludedPackagesKey].orEmpty()
    }

    suspend fun setUserExcluded(packageName: String, excluded: Boolean) {
        context.exclusionDataStore.edit { preferences ->
            val packages = preferences[excludedPackagesKey].orEmpty().toMutableSet()
            if (excluded) packages += packageName else packages -= packageName
            preferences[excludedPackagesKey] = packages
        }
    }

    suspend fun buildTargetPlan(apps: List<InstalledApp>): TargetPlan {
        val automatic = automaticExclusions()
        val user = userExcludedPackages.first()
        return TargetPlan(
            targets = apps.filterNot { it.packageName in automatic || it.packageName in user },
            automaticExcludedCount = apps.count { it.packageName in automatic },
            userExcludedCount = apps.count { it.packageName !in automatic && it.packageName in user },
        )
    }

    fun automaticExclusions(): Set<String> = buildSet {
        add(context.packageName)
        resolveHomePackage()?.let(::add)
        defaultDialerPackage()?.let(::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(::add)
        currentImePackage()?.let(::add)
        addAll(alarmPackages())
        addAll(enabledAccessibilityPackages())
        // Defensive exclusions. These are normally system apps and therefore filtered earlier.
        add("com.android.settings")
        add("com.google.android.permissioncontroller")
        add("com.android.permissioncontroller")
        add("com.android.systemui")
        add("com.android.packageinstaller")
    }

    private fun resolveHomePackage(): String? = context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName

    private fun defaultDialerPackage(): String? = runCatching {
        context.getSystemService(TelecomManager::class.java).defaultDialerPackage
    }.getOrNull()

    private fun currentImePackage(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)?.packageName
    }.getOrNull()

    private fun alarmPackages(): Set<String> = buildSet {
        listOf(AlarmClock.ACTION_SET_ALARM, AlarmClock.ACTION_SHOW_ALARMS).forEach { action ->
            context.packageManager.queryIntentActivities(Intent(action), 0)
                .mapTo(this) { it.activityInfo.packageName }
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            runCatching {
                context.getSystemService(AlarmManager::class.java)
                    .nextAlarmClock?.showIntent?.creatorPackage
            }.getOrNull()?.let(::add)
        }
    }

    private fun enabledAccessibilityPackages(): Set<String> {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapTo(mutableSetOf()) { ComponentName.unflattenFromString(it.id)?.packageName ?: it.id.substringBefore('/') }
    }
}
