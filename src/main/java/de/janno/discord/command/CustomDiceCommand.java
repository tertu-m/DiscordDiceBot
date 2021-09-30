package de.janno.discord.command;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import de.janno.discord.dice.DiceResult;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.LayoutComponent;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static de.janno.discord.dice.DiceUtils.rollDiceOfType;

@Slf4j
public class CustomDiceCommand extends AbstractCommand {

    private static final String COMMAND_NAME = "custom_dice";
    private static final String DICE_FORMAT = "^\\d{1,2}[dD]\\d{1,2}$";
    private static final List<String> DICE_COMMAND_OPTIONS_IDS = IntStream.range(1, 16).mapToObj(i -> i + "_button").collect(Collectors.toList());

    public CustomDiceCommand(Snowflake botUserId) {
        super(new ActiveButtonsCache(COMMAND_NAME), botUserId);
    }

    @Override
    protected String getCommandDescription() {
        return "Configure a custom set of dice";
    }

    @Override
    protected String getButtonMessage(List<String> config) {
        return "Click on a button to roll the dice";
    }

    @Override
    protected List<ApplicationCommandOptionData> getStartOptions() {
        return DICE_COMMAND_OPTIONS_IDS.stream()
                .map(id -> ApplicationCommandOptionData.builder()
                        .name(id)
                        .required(false)
                        .description("xdy for a set of x dice with y sides, e.g. '3d6'")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .build())
                .collect(Collectors.toList());
    }


    @Override
    protected List<String> getConfigValuesFromStartOptions(ApplicationCommandInteractionOption options) {
        return DICE_COMMAND_OPTIONS_IDS.stream()
                .flatMap(id -> options.getOption(id).stream())
                .flatMap(a -> a.getValue().stream())
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(Object::toString)
                .filter(s -> s.matches(DICE_FORMAT))
                .distinct()
                .limit(15)
                .collect(Collectors.toList());
    }

    @Override
    protected DiceResult rollDice(Snowflake channelId, String buttonValue, List<String> config) {
        String[] split = buttonValue.toLowerCase().split("d");
        String rollResult = rollDiceOfType(Integer.parseInt(split[0]), Integer.parseInt(split[1])).stream()
                .sorted()
                .map(Objects::toString)
                .collect(Collectors.joining(","));
        log.info(String.format("%s - %s: %s", channelId.asString(), buttonValue, rollResult));
        return new DiceResult(buttonValue, rollResult);
    }

    @Override
    protected List<LayoutComponent> getButtonLayout(List<String> config) {
        List<Button> buttons = config.stream()
                .map(d -> Button.primary(createButtonCustomId(COMMAND_NAME, d, ImmutableList.of()), d))
                .collect(Collectors.toList());
        return Lists.partition(buttons, 5).stream()
                .map(ActionRow::of)
                .collect(Collectors.toList());
    }

    @Override
    protected List<String> getConfigFromEvent(ComponentInteractionEvent event) {
        return event.getInteraction().getMessage()
                .map(s -> s.getComponents().stream()
                        .flatMap(lc -> lc.getChildren().stream())
                        .map(l -> l.getData().customId())
                        .map(c -> c.toOptional().orElse(null))
                        .filter(Objects::nonNull)
                        .map(id -> id.substring(COMMAND_NAME.length() + 1))
                        .collect(Collectors.toList())
                )
                .orElse(ImmutableList.of());
    }

    @Override
    protected boolean matchingButtonCustomId(String buttonCustomId) {
        return buttonCustomId.startsWith(COMMAND_NAME + CONFIG_DELIMITER);
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }
}
