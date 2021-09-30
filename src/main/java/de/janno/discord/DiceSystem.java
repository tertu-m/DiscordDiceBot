package de.janno.discord;

import de.janno.discord.command.*;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class DiceSystem {

    /**
     * TODO:
     * - slash Commands
     * -- help
     * -- version? git hash?
     * -- statistics command
     * -- sum of dices
     * - optionally moving the button after all messages to the end
     * - optional delay button remove
     * - optional config the max number of dice selection
     * - system that compares slashCommand in code with the current and updates if there are changes
     **/

    /**
     * Discord4J  https://docs.discord4j.com/
     * discord4j examples https://docs.discord4j.com/examples
     * <p>
     * Discord
     * - slash commands https://discord.com/developers/docs/interactions/application-commands#application-command-object
     * - buttons https://discord.com/developers/docs/interactions/message-components
     * <p>
     * Reactor
     * https://projectreactor.io/docs/core/release/reference/index.html
     * <p>
     * alternatives Java Discord Framework
     * https://github.com/DV8FromTheWorld/JDA/
     **/

    public DiceSystem(String token, boolean updateSlashCommands) {
        DiscordClient discordClient = DiscordClient.create(token);

        Snowflake botUserId = discordClient.getCoreResources().getSelfId();
        SlashCommandRegistry slashCommandRegistry = SlashCommandRegistry.builder()
                .addSlashCommand(new CountSuccessesCommand(botUserId))
                .addSlashCommand(new CustomDiceCommand(botUserId))
                .addSlashCommand(new FateCommand(botUserId))
                .registerSlashCommands(discordClient, updateSlashCommands);

        discordClient.withGateway(gw -> gw.on(new ReactiveEventAdapter() {

                    @Override
                    @NonNull
                    public Publisher<?> onChatInputInteraction(@NonNull ChatInputInteractionEvent event) {
                        return Flux.fromIterable(slashCommandRegistry.getSlashCommands())
                                .filter(command -> command.getName().equals(event.getCommandName()))
                                .next()
                                .flatMap(command -> command.handleSlashCommandEvent(event))
                                .onErrorResume(e -> {
                                    log.error("SlashCommandEvent Exception: ", e);
                                    return Mono.empty();
                                });
                    }

                    @Override
                    @NonNull
                    public Publisher<?> onComponentInteraction(@NonNull ComponentInteractionEvent event) {
                        return Flux.fromIterable(slashCommandRegistry.getSlashCommands())
                                .ofType(IComponentInteractEventHandler.class)
                                .flatMap(command -> command.handleComponentInteractEvent(event))
                                .onErrorResume(e -> {
                                    log.error("ButtonInteractEvent Exception: ", e);
                                    return Mono.empty();
                                });
                    }
                }).then(gw.onDisconnect())
        )
                .block();

    }
}
