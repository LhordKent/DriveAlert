package com.lhordkent.drivealert.postauth

import com.lhordkent.drivealert.data.profile.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UserViewPersistenceTest {
    private val store = object : UserViewPreferenceStore {
        val values = mutableMapOf<String, UserView>()
        override fun read(userId: String) = values[userId]
        override fun write(userId: String, view: UserView) { values[userId] = view }
    }
    private val profiles = object : UserProfileRepository {
        val records = mutableMapOf<String, MutableStateFlow<UserProfile?>>()
        override fun observe(uid: String) = records.getOrPut(uid) { MutableStateFlow(profile(uid)) }
        override suspend fun create(profile: NewUserProfile) = Unit
        override suspend fun ensureProfile(uid: String, displayName: String, email: String) = "UNITTEST"
        override suspend fun selectRole(uid: String, selectedRole: UserRole) = UserRole.BOTH
        override suspend fun update(uid: String, profile: UserProfileUpdate) = Unit
    }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun viewModel() = PostAuthViewModel(userProfileRepository = profiles, userViewPreferenceStore = store)

    @Test fun selectionSurvivesSignOutAndNewViewModel() {
        val first = viewModel()
        first.bindCloudProfile("account-a", "Unit", "unit@example.test")
        first.chooseView(UserView.TRUSTED_CONTACT)
        assertEquals(UserView.TRUSTED_CONTACT, store.read("account-a"))
        first.clearForSignOut()
        val second = viewModel()
        second.bindCloudProfile("account-a", "Unit", "unit@example.test")
        assertEquals(UserRole.BOTH, second.state.userRole)
        assertEquals(UserView.TRUSTED_CONTACT, second.state.activeView)
        second.clearForSignOut()
    }

    @Test fun profileRefreshDoesNotResetViewAndAccountsStaySeparate() {
        val model = viewModel()
        model.bindCloudProfile("account-a", "Unit", "unit@example.test")
        model.chooseView(UserView.TRUSTED_CONTACT)
        profiles.records.getValue("account-a").value = profile("account-a").copy(firstName = "Refreshed")
        assertEquals("Refreshed", model.state.profileFirstName)
        assertEquals(UserView.TRUSTED_CONTACT, model.state.activeView)
        model.clearForSignOut()
        model.bindCloudProfile("account-b", "Unit", "unit@example.test")
        assertEquals(UserView.DRIVER, model.state.activeView)
        model.chooseView(UserView.DRIVER)
        model.clearForSignOut()
        model.bindCloudProfile("account-a", "Unit", "unit@example.test")
        assertEquals(UserView.TRUSTED_CONTACT, model.state.activeView)
        model.clearForSignOut()
    }

    @Test fun accountsWithoutPreferenceUseTheirRoleDefault() {
        val model = viewModel()
        profiles.records["trusted"] = MutableStateFlow(profile("trusted").copy(userRole = UserRole.TRUSTED_CONTACT))
        model.bindCloudProfile("trusted", "Unit", "unit@example.test")
        assertEquals(UserView.TRUSTED_CONTACT, model.state.activeView)
        model.clearForSignOut()
        model.bindCloudProfile("both", "Unit", "unit@example.test")
        assertEquals(UserView.DRIVER, model.state.activeView)
        model.clearForSignOut()
    }

    private fun profile(uid: String) = UserProfile(uid, "Unit", null, "Test", "unit@example.test", null,
        UserRole.BOTH, AccountStatus.ACTIVE, null, null, "UNITTEST")
}
