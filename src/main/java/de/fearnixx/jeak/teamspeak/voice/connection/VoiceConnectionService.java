package de.fearnixx.jeak.teamspeak.voice.connection;

import com.github.manevolent.ts3j.identity.LocalIdentity;
import de.fearnixx.jeak.IBot;
import de.fearnixx.jeak.event.bot.IBotStateEvent;
import de.fearnixx.jeak.reflect.FrameworkService;
import de.fearnixx.jeak.reflect.Inject;
import de.fearnixx.jeak.reflect.Listener;
import de.fearnixx.jeak.service.event.IEventService;
import de.fearnixx.jeak.teamspeak.IServer;
import de.fearnixx.jeak.teamspeak.voice.connection.info.AbstractVoiceConnectionInformation;
import de.fearnixx.jeak.teamspeak.voice.connection.info.ConfigVoiceConnectionInformation;
import de.fearnixx.jeak.teamspeak.voice.connection.info.DbVoiceConnectionInformation;
import de.fearnixx.jeak.voice.connection.IVoiceConnection;
import de.fearnixx.jeak.voice.connection.IVoiceConnectionService;
import de.mlessmann.confort.LoaderFactory;
import de.mlessmann.confort.config.FileConfig;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@FrameworkService(serviceInterface = IVoiceConnectionService.class)
public class VoiceConnectionService implements IVoiceConnectionService {

    @Inject
    private IServer server;

    @Inject
    private IBot bot;

    @Inject
    private IEventService eventService;

    private boolean isDatabaseConnected = false;

    private Map<String, VoiceConnection> clientConnections = new HashMap<>();

    @Override
    public Optional<IVoiceConnection> getVoiceConnection(String identifier) {
        if (clientConnections.containsKey(identifier)) {
            final VoiceConnection clientConnection = clientConnections.get(identifier);

            if (clientConnection.isLocked()) {
                return Optional.empty();
            }

            return Optional.of(clientConnection);
        }

        final LocalIdentity teamspeakIdentity = createTeamspeakIdentity();

        AbstractVoiceConnectionInformation newClientConnectionInformation;

        if (isDatabaseConnected) {
            //TODO: Store client connection information in database
            newClientConnectionInformation = new DbVoiceConnectionInformation();
        } else {
            newClientConnectionInformation = new ConfigVoiceConnectionInformation(
                    new FileConfig(LoaderFactory.getLoader("application/json"),
                            new File(bot.getConfigDirectory(), "frw/voice/" + identifier + ".json")),
                    identifier
            );
            newClientConnectionInformation.setClientNickname(identifier);
            newClientConnectionInformation.setLocalIdentity(teamspeakIdentity);
        }

        final VoiceConnection clientConnection = new VoiceConnection(
                newClientConnectionInformation,
                server.getHost(),
                server.getPort(),
                eventService
        );

        clientConnections.put(identifier, clientConnection);

        return Optional.of(clientConnection);
    }

    @Listener
    public void preShutdown(IBotStateEvent.IPreShutdown event) {
        clientConnections.values().stream()
                .filter(VoiceConnection::isConnected)
                .forEach(VoiceConnection::disconnect);
    }

    @Listener
    public void onPreInit(IBotStateEvent.IPreInitializeEvent preInitializeEvent) {
        File voiceDir = new File(bot.getConfigDirectory(), "frw/voice");

        if (!voiceDir.isDirectory() && !voiceDir.mkdirs()) {
            throw new IllegalStateException("Failed to create voice connection directory!");
        }
    }

    private LocalIdentity createTeamspeakIdentity() {
        LocalIdentity localIdentity;
        try {
            localIdentity = LocalIdentity.generateNew(15);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to create local identity");
        }
        return localIdentity;
    }
}
