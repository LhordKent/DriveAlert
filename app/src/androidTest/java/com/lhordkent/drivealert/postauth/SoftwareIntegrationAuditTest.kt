package com.lhordkent.drivealert.postauth

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.auth.*
import com.lhordkent.drivealert.data.connection.*
import com.lhordkent.drivealert.data.local.entity.*
import com.lhordkent.drivealert.data.profile.UserRole
import com.lhordkent.drivealert.data.repository.*
import com.lhordkent.drivealert.data.sync.*
import com.lhordkent.drivealert.ui.DriveAlertApp
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Runs real Room, Firebase SDKs/rules, WorkManager and Compose without camera/BLE input. */
class SoftwareIntegrationAuditTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val testName = TestName()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as DriveAlertApplication).container
    private val selectedContact = mutableStateOf(false)
    private lateinit var driverAuth: FirebaseAuth
    private lateinit var driverCloud: FirebaseFirestore
    private lateinit var contactApp: FirebaseApp
    private lateinit var contactCloud: FirebaseFirestore
    private lateinit var driverUid: String
    private lateinit var contactUid: String
    private lateinit var driverVm: PostAuthViewModel
    private lateinit var contactVm: PostAuthViewModel
    private lateinit var driverConnections: FirestoreTrustedContactRepository
    private lateinit var contactConnections: FirestoreTrustedContactRepository
    private lateinit var fixture: JSONObject
    private val password = "AuditPassword123!"
    private val fixtureFile get() = File(context.getExternalFilesDir(null), "software-integration-fixtures.json")

    @Before fun prepare() = runBlocking {
        Assume.assumeTrue("Requires the isolated audit AVD and Firebase emulators",
            InstrumentationRegistry.getArguments().getString("auditSerial")=="emulator-5556")
        // This isolated AVD is the only device on which this test is authorized to run.
        shell("svc wifi enable"); shell("svc data enable")
        driverAuth = FirebaseAuth.getInstance()
        driverCloud = container.firestore
        if (!defaultEmulatorsConfigured) {
            driverAuth.useEmulator(HOST, AUTH_PORT)
            driverCloud.useEmulator(HOST, FIRESTORE_PORT)
            defaultEmulatorsConfigured = true
        }
        driverCloud.enableNetwork().await()
        driverAuth.signOut()
        val suffix = UUID.randomUUID().toString().take(8)
        val driverEmail = "integration-driver-$suffix@example.com"
        val contactEmail = "integration-contact-$suffix@example.com"
        driverUid = createVerifiedUser(driverAuth, driverEmail)
        contactApp = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "integration-$suffix")
        val contactAuth = FirebaseAuth.getInstance(contactApp)
        contactAuth.useEmulator(HOST, AUTH_PORT)
        contactCloud = FirebaseFirestore.getInstance(contactApp)
        contactCloud.useEmulator(HOST, FIRESTORE_PORT)
        contactUid = createVerifiedUser(contactAuth, contactEmail)
        val driverCode = profile(driverCloud, driverUid, driverEmail, "Driver", "DRIVER")
        val contactCode = profile(contactCloud, contactUid, contactEmail, "Contact", "TRUSTED_CONTACT")
        driverConnections = FirestoreTrustedContactRepository(driverCloud, container.database.trustedContactConnectionProjectionDao())
        contactConnections = FirestoreTrustedContactRepository(contactCloud, container.database.trustedContactConnectionProjectionDao())
        fixture = JSONObject().put("case", "IT-${testName.methodName.substring(2,4).toInt()}")
            .put("driverUid", driverUid).put("contactUid", contactUid)
            .put("driverEmail", driverEmail).put("contactEmail", contactEmail).put("password", password)
            .put("driverCode", driverCode).put("contactCode", contactCode)
        compose.runOnIdle {
            driverVm = PostAuthViewModel(container.alertRepository, container.monitoringSessionRepository,
                trustedContactRepository = driverConnections, stageSyncRepository = container.stageSyncRepository,
                initialState = PostAuthUiState(userRole=UserRole.DRIVER, isProfileLoading=false, driverSetupComplete=true))
            contactVm = PostAuthViewModel(trustedContactRepository = contactConnections,
                sharedStage3Repository = FirestoreSharedStage3Repository(contactCloud),
                initialState = PostAuthUiState(userRole=UserRole.TRUSTED_CONTACT, isProfileLoading=false))
            driverVm.bindLocalData(driverUid)
            driverVm.bindConnections(driverUid)
            driverVm.bindStageSyncRecords(driverUid)
            contactVm.bindConnections(contactUid)
        }
        compose.setContent {
            val contact = selectedContact.value
            val vm = if (contact) contactVm else driverVm
            val uid = if (contact) contactUid else driverUid
            key(uid) {
                DriveAlertTheme {
                    DriveAlertApp(callbacks=AuthCallbacks(),
                        sessionState=AuthSessionState(isResolving=false,
                            user=AuthenticatedUser(uid, if(contact) "Integration Contact" else "Integration Driver",
                                if(contact) contactEmail else driverEmail), entry=AuthenticationEntry.RESTORED),
                        postAuthState=vm.state,
                        onAcceptTrustedIncomingRequest=contactVm::acceptTrustedIncomingRequest,
                        onAcceptDriverIncomingRequest=driverVm::acceptDriverIncomingRequest)
                }
            }
        }
        compose.waitUntil(20_000) { !driverVm.state.driverConnectionsLoading && !contactVm.state.trustedConnectionsLoading }
    }

    @After fun finish() = runBlocking {
        if (!::driverAuth.isInitialized) return@runBlocking
        shell("svc wifi enable"); shell("svc data enable")
        if (::driverCloud.isInitialized) driverCloud.enableNetwork().await()
        if (::driverUid.isInitialized) container.stageSyncScheduler.cancel(driverUid)
        if (::driverVm.isInitialized) compose.runOnIdle { driverVm.clearForSignOut(); contactVm.clearForSignOut() }
        if (::contactCloud.isInitialized) contactCloud.terminate().await()
        if (::contactApp.isInitialized) contactApp.delete()
        if (::driverAuth.isInitialized) driverAuth.signOut()
    }

    @Test fun it04_confirmedAlertReachesRealHistoryAndDetailScreen() = runBlocking {
        val session = startSession()
        val detectedAt = System.currentTimeMillis()
        val id = container.alertRepository.recordConfirmedAlert(ConfirmedAlertInput(session, driverUid, detectedAt,
            WarningStage.STAGE_2, setOf(VisibleSign.YAWNING, VisibleSign.HEAD_NODDING), false, null))
        compose.waitUntil(10_000) { driverVm.state.driverAlerts.any { it.id==id } }
        val stored = driverVm.state.driverAlerts.single { it.id==id }
        assertEquals(driverUid, container.database.alertDao().getById(driverUid,id)!!.alert.driverUserId)
        assertEquals(session, stored.sessionId)
        assertEquals(WarningStage.STAGE_2, stored.stage)
        assertEquals(setOf(VisibleSign.YAWNING,VisibleSign.HEAD_NODDING),stored.signs)
        assertEquals(1,driverVm.state.alertInsights.alertsToday)
        assertEquals(1,driverVm.state.alertInsights.weeklyAlerts)
        assertEquals(WarningStage.STAGE_2,driverVm.state.alertInsights.highestStage)
        compose.onNodeWithText("Alerts",useUnmergedTree=true).performClick()
        waitForNode(hasText("Most frequent sign"))
        compose.onAllNodesWithText("1").assertCountEquals(2)
        compose.onNodeWithText("Most frequent sign").performScrollTo()
        compose.onNodeWithText(driverVm.state.alertInsights.mostFrequentSign!!.label).assertIsDisplayed()
        fixture.put("insightsUiVerified",true)
        val signs = hasText("Yawning",substring=true) and hasText("Head Nodding",substring=true)
        waitForNode(signs)
        compose.onNode(signs).performScrollTo().performClick()
        waitForNode(hasText("Alert Detail"))
        compose.onNodeWithText("Alert Detail").assertIsDisplayed()
        compose.onNodeWithText("Detected visible sign(s)").performScrollTo().assertIsDisplayed()
        compose.onNode(signs).assertIsDisplayed()
        compose.onNodeWithText("Warning Stage at Detection").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Stage 2",substring=true).onFirst().assertExists()
        assertNull(container.alertRepository.getAlert(contactUid,id))
        recordPassed(id,session)
    }

    @Test fun it06_requestAcceptanceReachesBothLiveMobileViews() = runBlocking {
        sendRequest()
        compose.waitUntil(15_000) { contactVm.state.trustedIncomingRequests.isNotEmpty() && driverVm.state.driverOutgoingRequests.isNotEmpty() }
        compose.runOnIdle { selectedContact.value=true }
        waitForNode(hasText("Connected Drivers"))
        compose.onNodeWithText("Requests",substring=true,useUnmergedTree=true).performClick()
        waitForNode(hasText("Integration Driver"))
        compose.onNodeWithText("Integration Driver").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Accept").performScrollTo().performClick()
        compose.waitUntil(20_000) { contactVm.state.connectedDrivers.any { it.id==driverUid } && driverVm.state.approvedContacts.isNotEmpty() }
        compose.onNodeWithText("Drivers",useUnmergedTree=true).performClick()
        waitForNode(hasText("Connected Drivers"))
        compose.onNodeWithText("Integration Driver").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { selectedContact.value=false }
        waitForNode(hasText("Driver Home"))
        compose.onNodeWithText("Contacts",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Integration Contact").performScrollTo().assertIsDisplayed()
        assertEquals("APPROVED",driverCloud.collection("trustedContactConnections").document(connectionId()).get(Source.SERVER).await().getString("status"))
        recordPassed()
    }

    @Test fun it07_realWorkerUploadsRoomRecordAndMobileReadsIt() = runBlocking {
        approveConnection()
        val session=startSession()
        val id=createRecord(session)
        waitForSynced(id)
        checkMobileSharedRecord(id)
        recordPassed(id,session)
    }

    @Test fun it08_networkConstraintHoldsQueueAndReconnectUploadsAutomatically() = runBlocking {
        approveConnection()
        val session=startSession()
        shell("svc wifi disable");shell("svc data disable")
        val connectivity=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        withTimeout(20_000) { while(connectivity.activeNetwork!=null) delay(200) }
        val id=createRecord(session)
        delay(2_000)
        val local=container.database.stageSyncRecordDao().getById(driverUid,id)!!.record
        assertNotEquals(StageSyncStatus.SYNCED,local.syncStatus)
        assertNull(local.syncedAtEpochMillis)
        val work=WorkManager.getInstance(context).getWorkInfosByTag("stage3-sync-user-$driverUid").get()
        assertTrue(work.any { it.state==WorkInfo.State.ENQUEUED })
        assertTrue(contactVm.state.connectedDrivers.single().sharedRecords.none { it.id==id })
        fixture.put("offlineQueueVerified",true)
        shell("svc wifi enable");shell("svc data enable")
        withTimeout(30_000) { while(connectivity.activeNetwork==null) delay(200) }
        // Do not call the processor or scheduler: WorkManager must resume the saved request itself.
        waitForSynced(id)
        checkMobileSharedRecord(id)
        recordPassed(id,session)
    }

    private suspend fun sendRequest() = driverConnections.sendTrustedContactRequest(driverUid,"Integration Driver",fixture.getString("driverEmail"),fixture.getString("contactCode"))
    private fun connectionId()="${driverUid}__${contactUid}"
    private suspend fun approveConnection() {
        sendRequest();contactConnections.acceptRequest(connectionId())
        compose.waitUntil(20_000) { driverVm.state.approvedContacts.isNotEmpty() && contactVm.state.connectedDrivers.any { it.id==driverUid } }
        val approvedAt=driverCloud.collection("trustedContactConnections").document(connectionId()).get(Source.SERVER).await()
            .getTimestamp("approvedAt")!!.toDate().time
        // Eligibility compares server approval with the phone's clock. Generate the event strictly after both.
        withTimeout(10_000) { while(System.currentTimeMillis()<=approvedAt+250) delay(50) }
        withTimeout(20_000) {
            while(WorkManager.getInstance(context).getWorkInfosByTag("stage3-sync-user-$driverUid").get().any { !it.state.isFinished }) delay(100)
        }
    }
    private suspend fun startSession():String {
        val calibration="integration-calibration-$driverUid"
        container.database.calibrationDao().insertAndActivate(CalibrationEntity(
            calibrationId=calibration,driverUserId=driverUid,earThreshold=.21,marThreshold=.62,
            headPitchThresholdDegrees=15.0,neutralHeadPitchDegrees=0.0,
            calibratedAtEpochMillis=System.currentTimeMillis(),isActive=true))
        return container.monitoringSessionRepository.start(StartMonitoringSessionInput(driverUid,calibration,"software-camera-fixture",System.currentTimeMillis()))
    }
    private suspend fun createRecord(session:String):String = container.stageSyncRepository.createSynchronizationRecord(
        StageSyncRecordInput(session,driverUid,StageSyncRecordType.STAGE3_TRANSITION,System.currentTimeMillis(),null,2,
            setOf(VisibleSign.YAWNING,VisibleSign.HEAD_NODDING)))
    private suspend fun waitForSynced(id:String) {
        withTimeout(90_000) { while(container.database.stageSyncRecordDao().getById(driverUid,id)?.record?.syncStatus!=StageSyncStatus.SYNCED) delay(250) }
        val doc=driverCloud.collection("users").document(driverUid).collection("stageSyncRecords").document(id).get(Source.SERVER).await()
        assertTrue(doc.exists());assertEquals(2L,doc.getLong("eventCount"));assertEquals("STAGE_3_TRANSITION",doc.getString("recordType"))
        assertEquals(setOf("YAWNING","HEAD_NODDING"),(doc.get("signs") as List<*>).toSet())
        compose.waitUntil(20_000) { contactVm.state.connectedDrivers.any { d-> d.sharedRecords.any { it.id==id } } }
    }
    private fun checkMobileSharedRecord(id:String) {
        val record=contactVm.state.connectedDrivers.single().sharedRecords.single { it.id==id }
        assertEquals(2,record.eventCount);assertEquals(setOf(VisibleSign.YAWNING,VisibleSign.HEAD_NODDING),record.signs)
        compose.runOnIdle { selectedContact.value=true }
        waitForNode(hasText("Connected Drivers"))
        compose.onNodeWithText("Integration Driver").performScrollTo().performClick()
        waitForNode(hasText("Stage 3 started"))
        compose.onNodeWithText("Stage 3 started").performScrollTo().performClick()
        waitForNode(hasText("Shared by Integration Driver"))
        compose.onNodeWithText("Shared by Integration Driver").assertIsDisplayed()
        compose.onNodeWithText("Warning Stage").performScrollTo().assertIsDisplayed()
        fixture.put("mobileSharedDetailsVerified",true)
    }
    private suspend fun createVerifiedUser(auth:FirebaseAuth,email:String):String {
        val user=auth.createUserWithEmailAndPassword(email,password).await().user!!
        val connection=URL("http://$HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/accounts:update").openConnection() as HttpURLConnection
        connection.requestMethod="POST";connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Authorization","Bearer owner");connection.doOutput=true
        connection.outputStream.use { it.write(JSONObject().put("localId",user.uid).put("emailVerified",true).toString().toByteArray()) }
        assertEquals(200,connection.responseCode);connection.inputStream.close();connection.disconnect()
        user.reload().await();user.getIdToken(true).await();assertTrue(user.isEmailVerified)
        return user.uid
    }
    private suspend fun profile(cloud:FirebaseFirestore,uid:String,email:String,last:String,role:String):String {
        val code=ConnectionCode.generate()
        cloud.collection("users").document(uid).set(mapOf("uid" to uid,"firstName" to "Integration","lastName" to last,"email" to email,
            "userRole" to role,"accountStatus" to "ACTIVE","registeredAt" to FieldValue.serverTimestamp(),"deactivatedAt" to null,"connectionCode" to code)).await()
        cloud.collection("connectionCodes").document(code).set(mapOf("ownerUserId" to uid,"displayName" to "Integration $last","createdAt" to FieldValue.serverTimestamp(),"active" to true)).await()
        return code
    }
    private fun recordPassed(record:String?=null,session:String?=null) {
        fixture.put("androidPassed",true)
        if(record!=null)fixture.put("recordId",record)
        if(session!=null)fixture.put("sessionId",session)
        val all=if(fixtureFile.exists())JSONArray(fixtureFile.readText()) else JSONArray()
        all.put(fixture);fixtureFile.writeText(all.toString(2))
        // Gradle uninstalls the audit APK after the run; retain the synthetic fixture in captured logcat.
        println("INTEGRATION_AUDIT_FIXTURE $fixture")
        println("INTEGRATION_AUDIT_PASSED ${fixture.getString("case")}")
    }
    private fun shell(command:String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun waitForNode(matcher:SemanticsMatcher) {
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }
    companion object {
        private const val HOST="10.0.2.2"
        private const val AUTH_PORT=9195
        private const val FIRESTORE_PORT=8185
        private var defaultEmulatorsConfigured=false
    }
}
