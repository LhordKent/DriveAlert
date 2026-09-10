package com.lhordkent.drivealert

import android.app.Application
import com.lhordkent.drivealert.data.local.DriveAlertDatabase
import com.lhordkent.drivealert.data.repository.AlertRepository
import com.lhordkent.drivealert.data.repository.MonitoringSessionRepository
import com.lhordkent.drivealert.data.repository.RoomAlertRepository
import com.lhordkent.drivealert.data.repository.RoomMonitoringSessionRepository
import com.lhordkent.drivealert.data.repository.DriverPreferenceRepository
import com.lhordkent.drivealert.data.repository.RoomDriverPreferenceRepository
import com.lhordkent.drivealert.data.repository.CalibrationRepository
import com.lhordkent.drivealert.data.repository.RoomCalibrationRepository
import com.lhordkent.drivealert.data.repository.ProvisionedDeviceRepository
import com.lhordkent.drivealert.data.repository.RoomProvisionedDeviceRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.lhordkent.drivealert.data.profile.FirestoreUserProfileRepository
import com.lhordkent.drivealert.data.profile.UserProfileRepository
import com.lhordkent.drivealert.data.connection.FirestoreTrustedContactRepository
import com.lhordkent.drivealert.data.connection.TrustedContactRepository
import com.lhordkent.drivealert.data.sync.FirestoreStageSyncRemoteDataSource
import com.lhordkent.drivealert.data.sync.RoomStageSyncRepository
import com.lhordkent.drivealert.data.sync.StageSyncProcessor
import com.lhordkent.drivealert.data.sync.StageSyncRemoteDataSource
import com.lhordkent.drivealert.data.sync.StageSyncRepository
import com.lhordkent.drivealert.data.sync.StageSyncScheduler
import com.lhordkent.drivealert.data.sync.WorkManagerStageSyncScheduler
import com.lhordkent.drivealert.data.sync.FirestoreSharedStage3Repository
import com.lhordkent.drivealert.data.sync.SharedStage3Repository
import com.lhordkent.drivealert.notification.AndroidDriverWarningNotificationGateway
import com.lhordkent.drivealert.notification.DriverWarningNotificationCoordinator

class DriveAlertApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        AndroidDriverWarningNotificationGateway.createChannel(this)
    }
}

class AppContainer(application: Application) {
    val database: DriveAlertDatabase = DriveAlertDatabase.getInstance(application)
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
    val alertRepository: AlertRepository = RoomAlertRepository(database.alertDao())
    val driverWarningNotificationCoordinator = DriverWarningNotificationCoordinator(
        AndroidDriverWarningNotificationGateway(application),
    )
    val monitoringSessionRepository: MonitoringSessionRepository =
        RoomMonitoringSessionRepository(database.monitoringSessionDao())
    val driverPreferenceRepository: DriverPreferenceRepository =
        RoomDriverPreferenceRepository(database.driverPreferenceDao())
    val calibrationRepository: CalibrationRepository = RoomCalibrationRepository(database.calibrationDao())
    val provisionedDeviceRepository: ProvisionedDeviceRepository =
        RoomProvisionedDeviceRepository(database.provisionedDeviceDao())
    val userProfileRepository: UserProfileRepository by lazy { FirestoreUserProfileRepository(firestore) }
    val trustedContactRepository: TrustedContactRepository by lazy {
        FirestoreTrustedContactRepository(
            firestore = firestore,
            projectionDao = database.trustedContactConnectionProjectionDao(),
        )
    }
    val stageSyncScheduler: StageSyncScheduler by lazy { WorkManagerStageSyncScheduler(application) }
    val stageSyncRemoteDataSource: StageSyncRemoteDataSource by lazy { FirestoreStageSyncRemoteDataSource(firestore) }
    val stageSyncProcessor: StageSyncProcessor by lazy {
        StageSyncProcessor(database.stageSyncRecordDao(), stageSyncRemoteDataSource)
    }
    val stageSyncRepository: StageSyncRepository by lazy {
        RoomStageSyncRepository(database.stageSyncRecordDao(), stageSyncScheduler)
    }
    val sharedStage3Repository: SharedStage3Repository by lazy { FirestoreSharedStage3Repository(firestore) }
}
