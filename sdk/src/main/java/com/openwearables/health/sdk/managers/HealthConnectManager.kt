package com.openwearables.health.sdk.managers

import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.openwearables.health.sdk.interfaces.HealthDataProvider
import com.openwearables.health.sdk.data.entities.ProviderReadResult
import com.openwearables.health.sdk.data.entities.UnifiedHealthData
import com.openwearables.health.sdk.managers.extensions.mapToProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.reflect.KClass

class HealthConnectManager(
    private val context: Context,
    private val logger: (String) -> Unit
) : HealthDataProvider {

    override val providerId = "google"
    override val providerName = "Health Connect"

    private lateinit var client: HealthConnectClient

    private var trackedTypeIds: Set<String> = emptySet()

    override fun setTrackedTypes(typeIds: List<String>) {
        trackedTypeIds = typeIds.toSet()
        val validCount = typeIds.count { mapToRecordClass(it) != null }
        logger("Tracking $validCount Health Connect types (out of ${typeIds.size} requested)")
    }

    override fun getTrackedTypes(): Set<String> = trackedTypeIds

    override fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    fun connect(): Boolean {
        if (!isAvailable()) {
            logger("Health Connect not available on this device")
            return false
        }
        return try {
            client = HealthConnectClient.getOrCreate(context)
            logger("Connected to Health Connect")
            true
        } catch (e: Exception) {
            logger("Failed to connect to Health Connect: ${e.message}")
            false
        }
    }

    override suspend fun requestAuthorization(typeIds: List<String>): Boolean {
        trackedTypeIds = typeIds.filter { mapToRecordClass(it) != null }.toSet()

        val permissions = typeIds.mapNotNull { typeId ->
            mapToRecordClass(typeId)?.let { HealthPermission.getReadPermission(it) }
        }.toMutableSet()

        if (permissions.isEmpty()) {
            logger("No valid Health Connect types to authorize")
            return false
        }

        permissions.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)

        if (!::client.isInitialized) return false

        val alreadyGranted = client.permissionController.getGrantedPermissions()
        val needed = permissions - alreadyGranted
        if (needed.isEmpty()) {
            logger("All ${permissions.size} Health Connect permissions already granted (including background read)")
            return true
        }

        val intent = Intent(context, PermissionsActivity::class.java).apply {
            putExtras(bundleOf(Pair("NEEDS", needed)))
        }
        context.startActivity(intent)
        return true
    }

    private suspend fun readDataInternal(
        typeId: String,
        sinceTimestamp: Long? = null,
        olderThanTimestamp: Long? = null,
        limit: Int
    ): ProviderReadResult = withContext(Dispatchers.IO) {
        if (!::client.isInitialized) {
            val isConnected = withContext(Dispatchers.Main) { connect() }
            if (!isConnected) return@withContext ProviderReadResult(UnifiedHealthData(), null)
        }

        val isAscending = olderThanTimestamp == null && sinceTimestamp != null
        try {
            when (typeId) {
                "steps" -> readRecordType<StepsRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "heartRate" -> readRecordType<HeartRateRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "restingHeartRate" -> readRecordType<RestingHeartRateRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "heartRateVariabilitySDNN" -> readRecordType<HeartRateVariabilityRmssdRecord>(sinceTimestamp, limit) { it.mapToProvider() }
                "oxygenSaturation" -> readRecordType<OxygenSaturationRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "bloodPressure", "bloodPressureSystolic", "bloodPressureDiastolic" -> readRecordType<BloodPressureRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "bloodGlucose" -> readRecordType<BloodGlucoseRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "activeEnergy" -> readRecordType<ActiveCaloriesBurnedRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "basalEnergy" -> readRecordType<BasalMetabolicRateRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "bodyTemperature" -> readRecordType<BodyTemperatureRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "bodyMass" -> readRecordType<WeightRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "height" -> readRecordType<HeightRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "bodyFatPercentage" -> readRecordType<BodyFatRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "leanBodyMass" -> readRecordType<LeanBodyMassRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "flightsClimbed" -> readRecordType<FloorsClimbedRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "distanceWalkingRunning" -> readRecordType<DistanceRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "water", "dietaryWater" -> readRecordType<HydrationRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "vo2Max" -> readRecordType<Vo2MaxRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "respiratoryRate" -> readRecordType<RespiratoryRateRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "distanceCycling" -> readRecordType<DistanceRecord>(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp) { it.mapToProvider() }
                "workout" -> readWorkouts(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp)
                "sleep" -> readSleep(sinceTimestamp, limit, isAscending, olderThanTimestamp = olderThanTimestamp)
                else -> ProviderReadResult(UnifiedHealthData(), null)
            }
        } catch (_: SecurityException) {
            logger("  $typeId: missing permission, skipping")
            ProviderReadResult(UnifiedHealthData(), null)
        } catch (e: Exception) {
            logger("Failed to read $typeId from Health Connect: ${e.javaClass.simpleName}: ${e.message}")
            ProviderReadResult(UnifiedHealthData(), null)
        }
    }

    override suspend fun readData(
        typeId: String,
        sinceTimestamp: Long?,
        limit: Int
    ): ProviderReadResult = readDataInternal(typeId = typeId, sinceTimestamp = sinceTimestamp, limit = limit)

    override suspend fun readDataDescending(
        typeId: String,
        olderThanTimestamp: Long?,
        limit: Int
    ): ProviderReadResult = readDataInternal(typeId = typeId, olderThanTimestamp = olderThanTimestamp, limit = limit)

    // -----------------------------------------------------------------------
    // Generic reader
    // -----------------------------------------------------------------------

    private suspend inline fun <reified T : Record> readRecordType(
        sinceTimestamp: Long?,
        limit: Int,
        ascending: Boolean = true,
        olderThanTimestamp: Long? = null,
        crossinline convert: (List<T>) -> ProviderReadResult
    ): ProviderReadResult {
        val timeFilter = when {
            !ascending && olderThanTimestamp != null ->
                TimeRangeFilter.before(Instant.ofEpochMilli(olderThanTimestamp))
            sinceTimestamp != null ->
                TimeRangeFilter.after(Instant.ofEpochMilli(sinceTimestamp + 1))
            else ->
                TimeRangeFilter.before(Instant.now())
        }

        val request = ReadRecordsRequest(
            recordType = T::class,
            timeRangeFilter = timeFilter,
            ascendingOrder = ascending,
            pageSize = limit
        )

        val response = client.readRecords(request)
        if (response.records.isEmpty()) return ProviderReadResult(UnifiedHealthData(), null, null)

        logger("Read ${response.records.size} ${T::class.simpleName} records${if (!ascending) " (newest first)" else ""}")
        val result = convert(response.records)

        val minTs = if (!ascending && response.records.isNotEmpty()) {
            getRecordTimestamp(response.records.last())
        } else null

        return ProviderReadResult(result.data, result.maxTimestamp, minTs)
    }

    private fun getRecordTimestamp(record: Record): Long? = when (record) {
        is StepsRecord -> record.endTime.toEpochMilli()
        is HeartRateRecord -> record.endTime.toEpochMilli()
        is RestingHeartRateRecord -> record.time.toEpochMilli()
        is HeartRateVariabilityRmssdRecord -> record.time.toEpochMilli()
        is OxygenSaturationRecord -> record.time.toEpochMilli()
        is BloodPressureRecord -> record.time.toEpochMilli()
        is BloodGlucoseRecord -> record.time.toEpochMilli()
        is ActiveCaloriesBurnedRecord -> record.endTime.toEpochMilli()
        is BasalMetabolicRateRecord -> record.time.toEpochMilli()
        is BodyTemperatureRecord -> record.time.toEpochMilli()
        is WeightRecord -> record.time.toEpochMilli()
        is HeightRecord -> record.time.toEpochMilli()
        is BodyFatRecord -> record.time.toEpochMilli()
        is LeanBodyMassRecord -> record.time.toEpochMilli()
        is FloorsClimbedRecord -> record.endTime.toEpochMilli()
        is DistanceRecord -> record.endTime.toEpochMilli()
        is HydrationRecord -> record.endTime.toEpochMilli()
        is Vo2MaxRecord -> record.time.toEpochMilli()
        is RespiratoryRateRecord -> record.time.toEpochMilli()
        is ExerciseSessionRecord -> record.endTime.toEpochMilli()
        is SleepSessionRecord -> record.endTime.toEpochMilli()
        else -> null
    }

    // -----------------------------------------------------------------------
    // Workouts
    // -----------------------------------------------------------------------

    private suspend fun readWorkouts(
        sinceTimestamp: Long?,
        limit: Int,
        ascending: Boolean = true,
        olderThanTimestamp: Long? = null
    ): ProviderReadResult {
        val timeFilter = when {
            !ascending && olderThanTimestamp != null ->
                TimeRangeFilter.before(Instant.ofEpochMilli(olderThanTimestamp))
            sinceTimestamp != null ->
                TimeRangeFilter.after(Instant.ofEpochMilli(sinceTimestamp + 1))
            else ->
                TimeRangeFilter.before(Instant.now())
        }

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = timeFilter,
                ascendingOrder = ascending,
                pageSize = limit
            )
        )
        if (response.records.isEmpty()) return ProviderReadResult(UnifiedHealthData(), null, null)
        val workouts = response.records.mapToProvider()

        val minTs = if (!ascending) {
            response.records.last().endTime.toEpochMilli()
        } else null

        return ProviderReadResult(workouts.data, workouts.maxTimestamp, minTs)
    }

    // -----------------------------------------------------------------------
    // Sleep
    // -----------------------------------------------------------------------

    private suspend fun readSleep(
        sinceTimestamp: Long?,
        limit: Int,
        ascending: Boolean = true,
        olderThanTimestamp: Long? = null
    ): ProviderReadResult {
        val timeFilter = when {
            !ascending && olderThanTimestamp != null ->
                TimeRangeFilter.before(Instant.ofEpochMilli(olderThanTimestamp))
            sinceTimestamp != null ->
                TimeRangeFilter.after(Instant.ofEpochMilli(sinceTimestamp + 1))
            else ->
                TimeRangeFilter.before(Instant.now())
        }

        logger("Reading sleep sessions (since: $sinceTimestamp, ascending: $ascending)")
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeFilter,
                ascendingOrder = ascending,
                pageSize = limit
            )
        )
        logger("Sleep query returned ${response.records.size} sessions")
        if (response.records.isEmpty()) return ProviderReadResult(UnifiedHealthData(), null)
        val sleeps = response.records.mapToProvider()

        val minTs = if (!ascending) {
            response.records.last().endTime.toEpochMilli()
        } else null

        return ProviderReadResult(sleeps.data, sleeps.maxTimestamp, minTs)
    }

    // -----------------------------------------------------------------------
    // Type mapping
    // -----------------------------------------------------------------------

    private fun mapToRecordClass(typeId: String): KClass<out Record>? = when (typeId) {
        "steps" -> StepsRecord::class
        "heartRate" -> HeartRateRecord::class
        "restingHeartRate" -> RestingHeartRateRecord::class
        "heartRateVariabilitySDNN" -> HeartRateVariabilityRmssdRecord::class
        "oxygenSaturation" -> OxygenSaturationRecord::class
        "bloodPressure", "bloodPressureSystolic", "bloodPressureDiastolic" -> BloodPressureRecord::class
        "bloodGlucose" -> BloodGlucoseRecord::class
        "activeEnergy" -> ActiveCaloriesBurnedRecord::class
        "basalEnergy" -> BasalMetabolicRateRecord::class
        "bodyTemperature" -> BodyTemperatureRecord::class
        "bodyMass" -> WeightRecord::class
        "height" -> HeightRecord::class
        "bodyFatPercentage" -> BodyFatRecord::class
        "leanBodyMass" -> LeanBodyMassRecord::class
        "flightsClimbed" -> FloorsClimbedRecord::class
        "distanceWalkingRunning" -> DistanceRecord::class
        "water", "dietaryWater" -> HydrationRecord::class
        "vo2Max" -> Vo2MaxRecord::class
        "respiratoryRate" -> RespiratoryRateRecord::class
        "distanceCycling" -> DistanceRecord::class
        "workout" -> ExerciseSessionRecord::class
        "sleep" -> SleepSessionRecord::class
        else -> null
    }
}
