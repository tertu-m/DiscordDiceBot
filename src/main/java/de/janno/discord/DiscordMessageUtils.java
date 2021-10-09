package de.janno.discord;

import com.google.common.collect.ImmutableSet;
import de.janno.discord.command.ActiveButtonsCache;
import discord4j.common.util.Snowflake;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.object.component.MessageComponent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class DiscordMessageUtils {
    //needed to correctly show utf8 characters in discord
    public static String encodeUTF8(@NonNull String in) {
        return new String(in.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    public static EmbedCreateSpec createEmbedMessageWithReference(
            @NonNull String title,
            @NonNull String description,
            @NonNull Member rollRequester) {
        return EmbedCreateSpec.builder()
                .title(StringUtils.abbreviate(encodeUTF8(title), 256)) //https://discord.com/developers/docs/resources/channel#embed-limits
                .author(rollRequester.getDisplayName(), null, rollRequester.getAvatarUrl())
                .color(Color.of(rollRequester.getId().hashCode()))
                .description(StringUtils.abbreviate(encodeUTF8(description), 4096)) //https://discord.com/developers/docs/resources/channel#embed-limits
             //   .timestamp(Instant.now())
                .build();
    }

    public static Flux<Void> deleteAllButtonMessagesOfTheBot(@NonNull Mono<TextChannel> channel,
                                                             @NonNull Snowflake deleteBeforeMessageId,
                                                             @NonNull Snowflake botUserId,
                                                             @NonNull Function<String, Boolean> isFromSystem) {
        return channel.flux()
                .flatMap(c -> c.getMessagesBefore(deleteBeforeMessageId))
                .take(500) //only look at the last 500 messages
                .filter(m -> botUserId.equals(m.getAuthor().map(User::getId).orElse(null)))
                .filter(m -> m.getComponents().stream()
                        .flatMap(l -> getLayoutComponentIdsFromMessage(l).stream())
                        .anyMatch(isFromSystem::apply))
                .flatMap(Message::delete);
    }

    public static Mono<Void> deleteMessage(
            @NonNull Mono<MessageChannel> channel,
            @NonNull Snowflake channelId,
            @NonNull ActiveButtonsCache activeButtonsCache,
            @NonNull Snowflake toKeep,
            @NonNull List<String> config) {
        return channel
                .flux()
                .flatMap(c -> {
                    List<Snowflake> allButtonsWithoutTheLast = activeButtonsCache.getAllWithoutOneAndRemoveThem(channelId, toKeep, config);
                    return Flux.fromIterable(allButtonsWithoutTheLast).flatMap(c::getMessageById);
                })
                .onErrorResume(e -> {
                    log.info("Button was not found");
                    return Mono.empty();
                })
                .flatMap(Message::delete).next().ofType(Void.class);
    }

    public static Function<TextChannel, Mono<Message>> createButtonMessage(@NonNull ActiveButtonsCache activeButtonsCache,
                                                                           @NonNull String buttonMessage,
                                                                           @NonNull List<LayoutComponent> buttons,
                                                                           @NonNull List<String> config) {
        return channel -> channel
                .createMessage(MessageCreateSpec.builder()
                        .content(buttonMessage)
                        .components(buttons)
                        .build())
                .map(m -> {
                    activeButtonsCache.addChannelWithButton(m.getChannelId(), m.getId(), config);
                    return m;
                });
    }

    private static Set<String> getLayoutComponentIdsFromMessage(MessageComponent messageComponent) {
        if (messageComponent instanceof LayoutComponent) {
            LayoutComponent layoutComponent = (LayoutComponent) messageComponent;
            if (!layoutComponent.getChildren().isEmpty()) {
                return layoutComponent.getChildren().stream().flatMap(mc -> getLayoutComponentIdsFromMessage(mc).stream()).collect(Collectors.toSet());
            }
        }
        return messageComponent.getData().customId().toOptional().map(ImmutableSet::of).orElse(ImmutableSet.of());
    }


}
