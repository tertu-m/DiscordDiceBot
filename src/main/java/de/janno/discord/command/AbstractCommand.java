package de.janno.discord.command;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.collect.ImmutableList;
import de.janno.discord.dice.DiceResult;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.janno.discord.DiscordMessageUtils.*;

@Slf4j
public abstract class AbstractCommand implements ISlashCommand, IComponentInteractEventHandler {

    protected static final String ACTION_CLEAR = "clear";
    protected static final String ACTION_START = "start";
    protected static final String CONFIG_DELIMITER = ",";
    protected final ActiveButtonsCache activeButtonsCache;
    protected final Snowflake botUserId;

    protected AbstractCommand(ActiveButtonsCache activeButtonsCache, Snowflake botUserId) {
        this.activeButtonsCache = activeButtonsCache;
        this.botUserId = botUserId;
    }

    protected static String createButtonCustomId(String system, String value, List<String> config) {
        return Stream.concat(Stream.of(system, value), config.stream())
                .collect(Collectors.joining(CONFIG_DELIMITER));
    }

    protected List<ApplicationCommandOptionData> getStartOptions() {
        return ImmutableList.of();
    }

    protected abstract String getCommandDescription();

    @Override
    public ApplicationCommandRequest getApplicationCommand() {
        return ApplicationCommandRequest.builder()
                .name(getName())
                .description(getCommandDescription())
                .addOption(ApplicationCommandOptionData.builder()
                        .name(ACTION_START)
                        .description("Start")
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addAllOptions(getStartOptions())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name(ACTION_CLEAR)
                        .description("Clear")
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .build()
                )
                .build();
    }

    @Override
    public Mono<Void> handleComponentInteractEvent(@NonNull ComponentInteractionEvent event) {
        if (!matchingButtonCustomId(event.getCustomId())
                || !botUserId.equals(event.getInteraction().getApplicationId())) {
            return Mono.empty();
        }
        List<String> config = getConfigFromEvent(event);

        return event
                .edit("rolling...")
                .onErrorResume(t -> {
                    log.error("Error on acknowledge button event", t);
                    return Mono.empty();
                })
                .then(event.getInteraction().getChannel()
                        .ofType(TextChannel.class)
                        .flatMap(channel -> {
                                    DiceResult result = rollDice(channel.getId(), getValueFromEvent(event), config);
                                    return createEmbedMessageWithReference(channel, result.getResultTitle(), result.getResultDetails(), event.getInteraction().getMember().orElseThrow())
                                            .retry(3);//not sure way this is needed but sometimes we get Connection reset in the event acknowledge and then here an error
                                }
                        )
                ).then(event.getInteraction().getChannel()
                        .ofType(TextChannel.class)
                        .flatMap(createButtonMessage(activeButtonsCache, getButtonMessage(config), getButtonLayout(config), config))
                        .flatMap(m -> deleteMessage(m.getChannel(), m.getChannelId(), activeButtonsCache, m.getId(), config))
                );
    }

    @Override
    public Mono<Void> handleSlashCommandEvent(@NonNull ChatInputInteractionEvent event) {
        if (getName().equals(event.getCommandName())) {
            if (event.getOption(ACTION_CLEAR).isPresent()) {
                activeButtonsCache.removeChannel(event.getInteraction().getChannelId());
                log.info("Clear {} in {}", getName(), event.getInteraction().getChannelId().asString());
                SharedMetricRegistries.getDefault().counter(getName() + ".clear").inc();
                SharedMetricRegistries.getDefault().counter("clear").inc();

                return event.reply("...")
                        .onErrorResume(t -> {
                            log.error("Error on replay to slash start command", t);
                            return Mono.empty();
                        })
                        .then(event.getInteraction()
                                .getChannel()
                                .ofType(TextChannel.class)
                                .flatMap(tc -> tc.createMessage(MessageCreateSpec.builder() //needed to have a messageId to remove all bot messages before
                                        .content("Clear " + getName() + " button messages from channel")
                                        //todo add messageReference
                                        .build()))
                                .flatMap(m -> deleteAllButtonMessagesOfTheBot(m.getChannel().ofType(TextChannel.class), m.getId(), botUserId, this::matchingButtonCustomId).then())
                                .retry(3));

            } else if (event.getOption(ACTION_START).isPresent()) {
                ApplicationCommandInteractionOption options = event.getOption(ACTION_START).get();
                List<String> config = getConfigValuesFromStartOptions(options);
                SharedMetricRegistries.getDefault().counter(getName() + ".start." + config).inc();
                SharedMetricRegistries.getDefault().counter(getName() + ".start").inc();
                SharedMetricRegistries.getDefault().counter("start").inc();
                log.info("Start {} with {} in channel {}", getName(), getConfigDescription(config), event.getInteraction().getChannelId().asLong());
                return event.reply("...")
                        .onErrorResume(t -> {
                            log.error("Error on replay to slash start command", t);
                            return Mono.empty();
                        })
                        .then(event.getInteraction().getChannel().ofType(TextChannel.class)
                                .flatMap(createButtonMessage(activeButtonsCache, getButtonMessage(config), getButtonLayout(config), config))
                                .retry(3))
                        .then();

            }

        }
        return Mono.empty();
    }

    protected abstract String getButtonMessage(List<String> config);

    protected abstract List<String> getConfigValuesFromStartOptions(ApplicationCommandInteractionOption options);

    protected abstract DiceResult rollDice(Snowflake channelId, String buttonValue, List<String> config);

    protected abstract List<LayoutComponent> getButtonLayout(List<String> config);

    protected String getConfigDescription(List<String> config) {
        return "";
    }

    protected List<String> getConfigFromEvent(ComponentInteractionEvent event) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        String[] split = event.getCustomId().split(CONFIG_DELIMITER);
        for (int i = 2; i < split.length; i++) {
            builder.add(split[i]);
        }
        return builder.build();
    }

    protected String getValueFromEvent(ComponentInteractionEvent event) {
        return event.getCustomId().split(CONFIG_DELIMITER)[1];
    }

    protected abstract boolean matchingButtonCustomId(String buttonCustomId);
}
