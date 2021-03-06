package de.fearnixx.jeak.test.plugin;

import de.fearnixx.jeak.event.bot.IBotStateEvent;
import de.fearnixx.jeak.profile.IProfileService;
import de.fearnixx.jeak.profile.IUserProfile;
import de.fearnixx.jeak.reflect.Inject;
import de.fearnixx.jeak.reflect.JeakBotPlugin;
import de.fearnixx.jeak.reflect.Listener;
import de.fearnixx.jeak.test.AbstractTestPlugin;

import java.util.Optional;
import java.util.UUID;

@JeakBotPlugin(id = "userprofiletest")
public class UserProfileTestPlugin extends AbstractTestPlugin {

    private static final String TEST_TS3_UID = "GANC6dTbew+a3A2h/8c5CGJXzsE=";
    private static final String TEST_TS3_UID2 = "cZS9nRtgsAhaIVMbOwFxLmTeEvE=";

    @Inject
    private IProfileService profileService;

    public UserProfileTestPlugin() {
        addTest("test_profileSvc_injected");
        addTest("test_profileSvc_nonExistent");
        addTest("test_profileSvc_getProfile_byTS3");
        addTest("test_profileSvc_getProfile_byUUID");
        addTest("test_profileSvc_deleteProfile");
        addTest("test_profileSvc_persistentProfile");
    }

    @Listener(order = Listener.Orders.LATE)
    public void onInitialize(IBotStateEvent.IInitializeEvent event) {
        if (profileService != null) {
            success("test_profileSvc_injected");
        } else {
            return;
        }

        Optional<IUserProfile> optProfile = profileService.getProfile(TEST_TS3_UID);
        if (!optProfile.isPresent()) {
            success("test_profileSvc_nonExistent");
        }

        optProfile = profileService.getOrCreateProfile(TEST_TS3_UID);
        if (optProfile.isPresent()) {
            success("test_profileSvc_getProfile_byTS3");
        }

        UUID assocUUID = optProfile.get().getUniqueId();
        optProfile = profileService.getProfile(assocUUID);
        if (optProfile.isPresent()) {
            success("test_profileSvc_getProfile_byUUID");
        }

        profileService.deleteProfile(assocUUID);
        optProfile = profileService.getProfile(assocUUID);
        if (!optProfile.isPresent()) {
            success("test_profileSvc_deleteProfile");
        }

        optProfile = profileService.getOrCreateProfile(TEST_TS3_UID2);
        if (optProfile.isPresent()) {
            IUserProfile persistentProfile = optProfile.get();
            persistentProfile.setOption("test_plugin", "true");
            success("test_profileSvc_persistentProfile");
        }
    }
}
