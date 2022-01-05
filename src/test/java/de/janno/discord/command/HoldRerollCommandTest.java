package de.janno.discord.command;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.janno.discord.dice.DiceResult;
import de.janno.discord.dice.DiceUtils;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HoldRerollCommandTest {

    HoldRerollCommand underTest;

    private static Stream<Arguments> generateValidateData() {
        return Stream.of(
                Arguments.of(new HoldRerollCommand.Config(6, ImmutableSet.of(7), ImmutableSet.of(), ImmutableSet.of()), "reroll set [7] contains a number bigger then the sides of the die 6"),
                Arguments.of(new HoldRerollCommand.Config(6, ImmutableSet.of(), ImmutableSet.of(7), ImmutableSet.of()), "success set [7] contains a number bigger then the sides of the die 6"),
                Arguments.of(new HoldRerollCommand.Config(6, ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(7)), "failure set [7] contains a number bigger then the sides of the die 6"),
                Arguments.of(new HoldRerollCommand.Config(6, ImmutableSet.of(2, 3, 4), ImmutableSet.of(5, 6), ImmutableSet.of(1)), null)
        );
    }

    @BeforeEach
    void setup() {
        underTest = new HoldRerollCommand(new DiceUtils(1, 1, 1, 1, 5, 6, 6, 6));
    }

    @Test
    void getName() {
        String res = underTest.getName();
        assertThat(res).isEqualTo("hold_reroll");
    }

    @Test
    void getStartOptions() {
        List<ApplicationCommandOptionData> res = underTest.getStartOptions();

        assertThat(res.stream().map(ApplicationCommandOptionData::name)).containsExactly("sides", "reroll_set", "success_set", "failure_set");
    }

    @Test
    void getDiceResult_withoutReroll() {
        List<DiceResult> res = underTest.getDiceResult(new HoldRerollCommand.State("finish", ImmutableList.of(1, 2, 3, 4, 5, 6), 0),
                new HoldRerollCommand.Config(
                        6,
                        ImmutableSet.of(2, 3, 4),
                        ImmutableSet.of(5, 6),
                        ImmutableSet.of(1)));
        assertThat(res).hasSize(1);
        assertThat(res.get(0).getResultTitle()).isEqualTo("Success: 2 and Failure: 1");
        assertThat(res.get(0).getResultDetails()).isEqualTo("[**1**,2,3,4,**5**,**6**]");
    }

    @Test
    void getDiceResult_withReroll() {
        List<DiceResult> res = underTest.getDiceResult(new HoldRerollCommand.State("finish", ImmutableList.of(1, 2, 3, 4, 5, 6), 2),
                new HoldRerollCommand.Config(
                        6,
                        ImmutableSet.of(2, 3, 4),
                        ImmutableSet.of(5, 6),
                        ImmutableSet.of(1)));
        assertThat(res).hasSize(1);
        assertThat(res.get(0).getResultTitle()).isEqualTo("Success: 2, Failure: 1 and Rerolls: 2");
        assertThat(res.get(0).getResultDetails()).isEqualTo("[**1**,2,3,4,**5**,**6**]");
    }

    @Test
    void getConfigFromEvent_roll3d6() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,3,EMPTY,6,2;3;4,5;6,1,0");

        assertThat(underTest.getConfigFromEvent(event)).isEqualTo(new HoldRerollCommand.Config(
                6,
                ImmutableSet.of(2, 3, 4),
                ImmutableSet.of(5, 6),
                ImmutableSet.of(1)));
    }

    @Test
    void getStateFromEvent_roll3d6() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,3,EMPTY,6,2;3;4,5;6,1,0");

        assertThat(underTest.getStateFromEvent(event)).isEqualTo(new HoldRerollCommand.State("3", ImmutableList.of(1, 1, 1), 0));
    }

    @Test
    void getConfigFromEvent_finish() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,finish,1;2;3;4;5;6,6,2;3;4,5;6,1,0");
        assertThat(underTest.getConfigFromEvent(event)).isEqualTo(new HoldRerollCommand.Config(
                6,
                ImmutableSet.of(2, 3, 4),
                ImmutableSet.of(5, 6),
                ImmutableSet.of(1)));
    }

    @Test
    void getStateFromEvent_finish() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,finish,1;2;3;4;5;6,6,2;3;4,5;6,1,0");
        assertThat(underTest.getStateFromEvent(event)).isEqualTo(new HoldRerollCommand.State("finish", ImmutableList.of(1, 2, 3, 4, 5, 6), 0));
    }

    @Test
    void getConfigFromEvent_clear() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,clear,1;2;3;4;5;6,6,2;3;4,5;6,1,0");
        assertThat(underTest.getConfigFromEvent(event))
                .isEqualTo(new HoldRerollCommand.Config(
                        6,
                        ImmutableSet.of(2, 3, 4),
                        ImmutableSet.of(5, 6),
                        ImmutableSet.of(1)));
    }

    @Test
    void getStateFromEvent_clear() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,clear,1;2;3;4;5;6,6,2;3;4,5;6,1,0");
        assertThat(underTest.getStateFromEvent(event))
                .isEqualTo(new HoldRerollCommand.State("clear", ImmutableList.of(), 0));
    }

    @Test
    void getConfigFromEvent_reroll() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,reroll,1;2;3;4;5;6,6,2;3;4,5;6,1,1");
        assertThat(underTest.getConfigFromEvent(event)).isEqualTo(new HoldRerollCommand.Config(
                6,
                ImmutableSet.of(2, 3, 4),
                ImmutableSet.of(5, 6),
                ImmutableSet.of(1)));
    }

    @Test
    void getStateFromEvent_reroll() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,reroll,1;2;3;4;5;6,6,2;3;4,5;6,1,1");
        assertThat(underTest.getStateFromEvent(event)).isEqualTo(new HoldRerollCommand.State("reroll", ImmutableList.of(1, 1, 1, 1, 5, 6), 2));
    }

    @Test
    void matchingComponentCustomId_match() {
        assertThat(underTest.matchingComponentCustomId("hold_reroll,1;2")).isTrue();
    }

    @Test
    void matchingComponentCustomId_noMatch() {
        assertThat(underTest.matchingComponentCustomId("hold_rerol")).isFalse();
    }

    @Test
    void getButtonMessage_clear() {
        String res = underTest.getButtonMessage(new HoldRerollCommand.State("clear", ImmutableList.of(1, 2, 3, 4, 5, 6), 2), new HoldRerollCommand.Config(
                6,
                ImmutableSet.of(2, 3, 4),
                ImmutableSet.of(5, 6),
                ImmutableSet.of(1)));

        assertThat(res).isEqualTo("Click on the buttons to roll dice. Reroll set: [2, 3, 4], Success Set: [5, 6] and Failure Set: [1]");
    }

    @Test
    void getButtonMessage_finish() {
        String res = underTest.getButtonMessage(new HoldRerollCommand.State("finish", ImmutableList.of(1, 2, 3, 4, 5, 6), 2), new HoldRerollCommand.Config(
                6,
                ImmutableSet.of(2, 3, 4),
                ImmutableSet.of(5, 6),
                ImmutableSet.of(1)));

        assertThat(res).isEqualTo("Click on the buttons to roll dice. Reroll set: [2, 3, 4], Success Set: [5, 6] and Failure Set: [1]");
    }

    @Test
    void getButtonMessage_noRerollPossible() {
        String res = underTest.getButtonMessage(new HoldRerollCommand.State("reroll", ImmutableList.of(1, 1, 1, 5, 5, 6), 2), new HoldRerollCommand.Config(
                6,
                ImmutableSet.of(2, 3, 4),
                ImmutableSet.of(5, 6),
                ImmutableSet.of(1)));

        assertThat(res).isEqualTo("Click on the buttons to roll dice. Reroll set: [2, 3, 4], Success Set: [5, 6] and Failure Set: [1]");
    }

    @Test
    void getButtonMessage_rerollPossible() {
        String res = underTest.getButtonMessage(new HoldRerollCommand.State("reroll", ImmutableList.of(1, 2, 3, 4, 5, 6), 2), new HoldRerollCommand.Config(
                6,
                ImmutableSet.of(2, 3, 4),
                ImmutableSet.of(5, 6),
                ImmutableSet.of(1)));

        assertThat(res).isEqualTo("[**1**,2,3,4,**5**,**6**] = 2 successes and 1 failures");
    }

    @ParameterizedTest(name = "{index} config={0} -> {1}")
    @MethodSource("generateValidateData")
    void validate(HoldRerollCommand.Config config, String expected) {
        assertThat(underTest.validate(config)).isEqualTo(expected);
    }

    @Test
    void getStateFromEvent() {
        IButtonEventAdaptor event = mock(IButtonEventAdaptor.class);
        when(event.getCustomId()).thenReturn("hold_reroll,finish,1;2;3;4;5;6,6,2;3;4,5;6,1,0");

        HoldRerollCommand.State res = underTest.getStateFromEvent(event);

        assertThat(res).isEqualTo(new HoldRerollCommand.State("finish", ImmutableList.of(1, 2, 3, 4, 5, 6), 0));
    }

    @Test
    void createButtonCustomId() {
        String res = underTest.createButtonCustomId("finish", new HoldRerollCommand.Config(6, ImmutableSet.of(2, 3, 4), ImmutableSet.of(5, 6), ImmutableSet.of(1)),
                new HoldRerollCommand.State("finish", ImmutableList.of(1, 1, 1, 1, 5, 6), 3));

        assertThat(res).isEqualTo("hold_reroll,finish,1;1;1;1;5;6,6,2;3;4,5;6,1,3");
    }
}