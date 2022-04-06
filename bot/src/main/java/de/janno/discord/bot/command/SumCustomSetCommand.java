package de.janno.discord.bot.command;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import de.janno.discord.bot.cache.ButtonMessageCache;
import de.janno.discord.bot.dice.DiceParserHelper;
import de.janno.discord.connector.api.IButtonEventAdaptor;
import de.janno.discord.connector.api.message.ButtonDefinition;
import de.janno.discord.connector.api.message.ComponentRowDefinition;
import de.janno.discord.connector.api.message.EmbedDefinition;
import de.janno.discord.connector.api.message.MessageDefinition;
import de.janno.discord.connector.api.slash.CommandDefinitionOption;
import de.janno.discord.connector.api.slash.CommandInteractionOption;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Slf4j
public class SumCustomSetCommand extends AbstractCommand<SumCustomSetCommand.Config, SumCustomSetCommand.State> {
    private static final String COMMAND_NAME = "sum_custom_set";
    private static final String ROLL_BUTTON_ID = "roll";
    private static final String NO_ACTION = "no action";
    private static final String EMPTY_MESSAGE = "Click the buttons to add dice to the set and then on Roll";
    private static final String EMPTY_MESSAGE_LEGACY = "Click on the buttons to add dice to the set";
    private static final String CLEAR_BUTTON_ID = "clear";
    private static final String BACK_BUTTON_ID = "back";
    private static final List<String> DICE_COMMAND_OPTIONS_IDS = IntStream.range(1, 23).mapToObj(i -> i + "_button").collect(Collectors.toList());
    private static final String INVOKING_USER_NAME_DELIMITER = "\u2236 ";
    private static final String LABEL_DELIMITER = "@";
    private final DiceParserHelper diceParserHelper;
    private static final ButtonMessageCache BUTTON_MESSAGE_CACHE = new ButtonMessageCache(COMMAND_NAME);

    public SumCustomSetCommand() {
        this(new DiceParserHelper());
    }

    @VisibleForTesting
    public SumCustomSetCommand(DiceParserHelper diceParserHelper) {
        super(BUTTON_MESSAGE_CACHE);
        this.diceParserHelper = diceParserHelper;
    }

    @Override
    protected @NonNull String getCommandDescription() {
        return "Configure a variable set of dice";
    }


    @Override
    protected EmbedDefinition getHelpMessage() {
        return EmbedDefinition.builder()
                .description("Creates up to 22 buttons with custom dice expression, that can be combined afterwards. e.g. '/sum_custom_set start 1_button:3d6 2_button:10d10 3_button:3d20'. \n" + DiceParserHelper.HELP)
                .build();
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }


    @VisibleForTesting
    String createButtonCustomId(String action) {
        Preconditions.checkArgument(!action.contains(CONFIG_DELIMITER));

        return String.join(CONFIG_DELIMITER,
                COMMAND_NAME,
                action);
    }

    @Override
    protected Optional<String> getStartOptionsValidationMessage(CommandInteractionOption options) {
        List<String> diceExpressionWithOptionalLabel = DICE_COMMAND_OPTIONS_IDS.stream()
                .flatMap(id -> options.getStingSubOptionWithName(id).stream())
                .distinct()
                .collect(Collectors.toList());
        String expressionsWithMultiRoll = diceExpressionWithOptionalLabel.stream()
                .filter(s -> s.contains("x["))
                .collect(Collectors.joining(","));
        if (!Strings.isNullOrEmpty(expressionsWithMultiRoll)) {
            return Optional.of(String.format("This command doesn't support multiple rolls, the following expression are not allowed: %s", expressionsWithMultiRoll));
        }
        String expressionWithUserNameDelimiter = diceExpressionWithOptionalLabel.stream()
                .filter(s -> s.contains(INVOKING_USER_NAME_DELIMITER))
                .collect(Collectors.joining(","));
        if (!Strings.isNullOrEmpty(expressionWithUserNameDelimiter)) {
            return Optional.of(String.format("This command doesn't allow '%s' in the dice expression and label, the following expression are not allowed: %s", INVOKING_USER_NAME_DELIMITER, expressionWithUserNameDelimiter));
        }
        return diceParserHelper.validateListOfExpressions(diceExpressionWithOptionalLabel, LABEL_DELIMITER, CONFIG_DELIMITER, "/sum_custom_set help");
    }

    @Override
    protected List<CommandDefinitionOption> getStartOptions() {
        return DICE_COMMAND_OPTIONS_IDS.stream()
                .map(id -> CommandDefinitionOption.builder()
                        .name(id)
                        .description("xdy for a set of x dice with y sides, e.g. '3d6'")
                        .type(CommandDefinitionOption.Type.STRING)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    protected Optional<EmbedDefinition> getAnswer(State state, Config config) {
        if (!(ROLL_BUTTON_ID.equals(state.getButtonValue()) && !state.getDiceExpression().isEmpty())) {
            return Optional.empty();
        }
        String label = config.getLabelAndExpression().stream()
                .filter(ld -> !ld.getDiceExpression().equals(ld.getLabel()))
                .filter(ld -> ld.getDiceExpression().equals(state.getDiceExpression()))
                .map(LabelAndDiceExpression::getLabel)
                .findFirst().orElse(null);
        return Optional.of(diceParserHelper.roll(state.getDiceExpression(), label));
    }

    @Override
    protected MessageDefinition getButtonMessage(Config config) {
        return MessageDefinition.builder()
                .content(EMPTY_MESSAGE)
                .componentRowDefinitions(createButtonLayout(config))
                .build();
    }

    @Override
    protected Optional<MessageDefinition> getButtonMessageWithState(State state, Config config) {
        if (ROLL_BUTTON_ID.equals(state.getButtonValue()) && !Strings.isNullOrEmpty(state.getDiceExpression())) {
            return Optional.of(MessageDefinition.builder()
                    .content(EMPTY_MESSAGE)
                    .componentRowDefinitions(createButtonLayout(config))
                    .build());

        }
        return Optional.empty();
    }

    @Override
    protected Optional<String> getEditButtonMessage(State state, Config config) {
        if (ROLL_BUTTON_ID.equals(state.getButtonValue())) {
            return Optional.of(EMPTY_MESSAGE);
        } else if (CLEAR_BUTTON_ID.equals(state.getButtonValue())) {
            return Optional.of(EMPTY_MESSAGE);
        } else {
            if (Strings.isNullOrEmpty(state.getDiceExpression())) {
                return Optional.of(EMPTY_MESSAGE);
            }
            if (Strings.isNullOrEmpty(state.getLockedForUserName())) {
                return Optional.of(state.getDiceExpression());
            } else {
                String cleanName = state.getLockedForUserName().replace(INVOKING_USER_NAME_DELIMITER, "");
                return Optional.of(String.format("%s%s%s", cleanName, INVOKING_USER_NAME_DELIMITER, state.getDiceExpression()));
            }
        }
    }


    @Override
    protected Config getConfigFromEvent(IButtonEventAdaptor event) {
        return new Config(event.getAllButtonIds().stream()
                .filter(lv -> !ImmutableSet.of(ROLL_BUTTON_ID, CLEAR_BUTTON_ID, BACK_BUTTON_ID).contains(lv.getCustomId().substring(COMMAND_NAME.length() + 1)))
                .map(lv -> new LabelAndDiceExpression(lv.getLabel(), lv.getCustomId().substring(COMMAND_NAME.length() + 1)))
                .collect(Collectors.toList()));
    }

    @Override
    protected State getStateFromEvent(IButtonEventAdaptor event) {
        String buttonValue = event.getCustomId().split(CONFIG_DELIMITER)[1];
        if (CLEAR_BUTTON_ID.equals(buttonValue)) {
            return new State(buttonValue, "", null);
        }

        String buttonMessageWithOptionalUser = event.getMessageContent();

        String lastInvokingUser = null;
        String buttonMessage;
        if (buttonMessageWithOptionalUser.contains(INVOKING_USER_NAME_DELIMITER)) {
            int firstDelimiter = buttonMessageWithOptionalUser.indexOf(INVOKING_USER_NAME_DELIMITER);
            lastInvokingUser = buttonMessageWithOptionalUser.substring(0, firstDelimiter);
            buttonMessage = buttonMessageWithOptionalUser.substring(firstDelimiter + INVOKING_USER_NAME_DELIMITER.length());
        } else {
            buttonMessage = buttonMessageWithOptionalUser;
        }
        buttonMessage = ImmutableSet.of(EMPTY_MESSAGE, EMPTY_MESSAGE_LEGACY).contains(buttonMessage) ? "" : buttonMessage;
        if (lastInvokingUser != null && !lastInvokingUser.equals(event.getInvokingGuildMemberName())) {
            return new State(NO_ACTION, buttonMessage, lastInvokingUser);
        }
        if (!Strings.isNullOrEmpty(buttonMessage) && !diceParserHelper.validExpression(buttonMessage)) {
            //invalid expression -> clear
            return new State(NO_ACTION, "", null);
        }
        if (BACK_BUTTON_ID.equals(buttonValue)) {
            int indexOfLastMinusOrPlus = Math.max(buttonMessage.lastIndexOf("+"), buttonMessage.lastIndexOf("-"));
            String newButtonMessage;
            if (indexOfLastMinusOrPlus > 0) {
                newButtonMessage = buttonMessage.substring(0, indexOfLastMinusOrPlus);
            } else {
                newButtonMessage = "";
            }
            return new State(buttonValue, newButtonMessage, event.getInvokingGuildMemberName());
        }
        if (ROLL_BUTTON_ID.equals(buttonValue)) {
            return new State(buttonValue, buttonMessage, event.getInvokingGuildMemberName());
        }

        String operator = "+";
        if (buttonValue.startsWith("-")) {
            operator = "-";
            buttonValue = buttonValue.substring(1);
        } else if (buttonValue.startsWith("+")) {
            buttonValue = buttonValue.substring(1);
        }

        String newContent;
        if (Strings.isNullOrEmpty(buttonMessage)) {
            newContent = operator.equals("-") ? String.format("-%s", buttonValue) : buttonValue;
        } else {
            newContent = String.format("%s%s%s", buttonMessage, operator, buttonValue);
        }
        return new State(buttonValue, newContent, event.getInvokingGuildMemberName());
    }

    @Override
    protected Config getConfigFromStartOptions(CommandInteractionOption options) {
        return getConfigOptionStringList(DICE_COMMAND_OPTIONS_IDS.stream()
                .flatMap(id -> options.getStingSubOptionWithName(id).stream())
                .collect(Collectors.toList()));
    }

    @VisibleForTesting
    Config getConfigOptionStringList(List<String> startOptions) {
        return new Config(startOptions.stream()
                .filter(s -> !s.contains(CONFIG_DELIMITER))
                .filter(s -> !s.contains(LABEL_DELIMITER) || s.split(LABEL_DELIMITER).length == 2)
                .map(s -> {
                    String label = null;
                    String diceExpression;
                    if (s.contains(LABEL_DELIMITER)) {
                        String[] split = s.split(LABEL_DELIMITER);
                        label = split[1].trim();
                        diceExpression = split[0].trim();
                    } else {
                        diceExpression = s.trim();
                    }
                    if (!diceExpression.startsWith("+") && !diceExpression.startsWith("-")) {
                        diceExpression = "+" + diceExpression;
                    }
                    if (label == null) {
                        label = diceExpression;
                    }
                    return new LabelAndDiceExpression(label, diceExpression);
                })
                .filter(s -> !s.getDiceExpression().isEmpty())
                .filter(s -> !s.getLabel().isEmpty())
                .filter(lv -> diceParserHelper.validExpression(lv.getDiceExpression()))
                .filter(s -> s.getDiceExpression().length() <= 80) //limit for the ids are 100 characters and we need also some characters for the type...
                .filter(s -> s.getLabel().length() <= 80) //https://discord.com/developers/docs/interactions/message-components#buttons
                .distinct()
                .limit(22)
                .collect(Collectors.toList()));
    }


    private List<ComponentRowDefinition> createButtonLayout(Config config) {
        List<ButtonDefinition> buttons = config.getLabelAndExpression().stream()
                .map(d -> ButtonDefinition.builder()
                        .id(createButtonCustomId(d.getDiceExpression()))
                        .label(d.getLabel())
                        .build())
                .collect(Collectors.toList());
        buttons.add(ButtonDefinition.builder()
                .id(createButtonCustomId(ROLL_BUTTON_ID))
                .label("Roll")
                .style(ButtonDefinition.Style.SUCCESS)
                .build());
        buttons.add(ButtonDefinition.builder()
                .id(createButtonCustomId(CLEAR_BUTTON_ID))
                .label("Clear")
                .style(ButtonDefinition.Style.DANGER)
                .build());
        buttons.add(ButtonDefinition.builder()
                .id(createButtonCustomId(BACK_BUTTON_ID))
                .label("Back")
                .style(ButtonDefinition.Style.SECONDARY)
                .build());
        return Lists.partition(buttons, 5).stream()
                .map(bl -> ComponentRowDefinition.builder().buttonDefinitions(bl).build())
                .collect(Collectors.toList());
    }

    @Value
    protected static class Config implements IConfig {
        @NonNull
        List<LabelAndDiceExpression> labelAndExpression;

        @Override
        public String toShortString() {
            return labelAndExpression.stream()
                    .map(LabelAndDiceExpression::toShortString)
                    .collect(Collectors.toList())
                    .toString();
        }

    }

    @Value
    static class State implements IState {
        @NonNull
        String buttonValue;
        @NonNull
        String diceExpression;
        String lockedForUserName;

        @Override
        public String toShortString() {
            return String.format("[%s, %s, %s]", buttonValue, diceExpression, lockedForUserName);
        }
    }

    @Value
    static class LabelAndDiceExpression {
        @NonNull
        String label;
        @NonNull
        String diceExpression;


        public String toShortString() {
            if (diceExpression.equals(label)) {
                return diceExpression;
            }
            return String.format("%s%s%s", diceExpression, LABEL_DELIMITER, label);
        }
    }
}
