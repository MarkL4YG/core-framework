package de.fearnixx.jeak.service.command.matcher;

import de.fearnixx.jeak.reflect.Inject;
import de.fearnixx.jeak.service.command.ICommandExecutionContext;
import de.fearnixx.jeak.service.command.matcher.meta.MatcherResponse;
import de.fearnixx.jeak.service.command.spec.matcher.IMatcherResponse;
import de.fearnixx.jeak.service.command.spec.matcher.MatcherResponseType;
import de.fearnixx.jeak.teamspeak.cache.IDataCache;
import de.fearnixx.jeak.teamspeak.data.IChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChannelParameterMatcher extends AbstractTypeMatcher<IChannel> {

    private static final Pattern ID_PATTERN = Pattern.compile("\\d+");

    private static final Logger logger = LoggerFactory.getLogger(ChannelParameterMatcher.class);

    @Inject
    private IDataCache dataCache;

    @Override
    public Class<IChannel> getSupportedType() {
        return IChannel.class;
    }

    @Override
    public IMatcherResponse tryMatch(ICommandExecutionContext ctx, int startParamPosition, String argName) {
        String paramString = ctx.getArguments().get(startParamPosition);
        if (ID_PATTERN.matcher(paramString).matches()) {
            IChannel channel = dataCache.getChannelMap().getOrDefault(Integer.parseInt(paramString), null);
            if (channel != null) {
                ctx.putOrReplaceOne(argName, channel);
                ctx.putOrReplaceOne(argName + "Id", channel.getID());
                return MatcherResponse.SUCCESS;
            }
        } else {
            List<IChannel> result = dataCache.getChannels()
                    .stream()
                    .filter(c -> c.getName().contains(paramString))
                    .collect(Collectors.toList());

            if (result.size() == 1) {
                IChannel channel = result.get(0);
                ctx.putOrReplaceOne(argName, channel);
                ctx.putOrReplaceOne(argName + "Id", channel.getID());
                return MatcherResponse.SUCCESS;

            } else if (result.size() > 1) {
                String allChannels =
                        result.stream()
                                .map(c -> c.getName() + '/' + c.getID())
                                .collect(Collectors.joining(","));
                String ambiguityMessage = getLocaleUnit()
                        .getContext(ctx.getSender().getCountryCode())
                        .getMessage("matcher.type.ambiguousSearch",
                                Map.of("results", allChannels));
                return new MatcherResponse(MatcherResponseType.ERROR, startParamPosition, ambiguityMessage);
            }
        }

        return getIncompatibleTypeResponse(ctx, startParamPosition);
    }
}