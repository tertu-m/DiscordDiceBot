package de.janno.discord.dice;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DiceParserHelperTest {

    static Stream<Arguments> generateMultipleExecutionData() {
        return Stream.of(
                Arguments.of("1d6", false),
                Arguments.of("2x[1d6]", true),
                Arguments.of("2[1d6]", false),
                Arguments.of("-2x[1d6]", false),
                Arguments.of("x[1d6]", false),
                Arguments.of("-x[1d6]", false),
                Arguments.of("ax[1d6]", false),
                Arguments.of("1x[1d6", false),
                Arguments.of("12x[1d6]", true)
        );
    }

    @ParameterizedTest(name = "{index} input:{0} -> {2}")
    @MethodSource("generateMultipleExecutionData")
    void multipleExecution(String diceExpression, boolean expected) {
        boolean res = DiceParserHelper.isMultipleRoll(diceExpression);
        assertThat(res).isEqualTo(expected);
    }

    @Test
    void getMultipleExecution() {
        int res = DiceParserHelper.getNumberOfMultipleRolls("11x[1d6 + [1d20]!!]");
        assertThat(res).isEqualTo(11);
    }

    @Test
    void getMultipleExecution_limit() {
        int res = DiceParserHelper.getNumberOfMultipleRolls("26x[1d6 + [1d20]!!]");
        assertThat(res).isEqualTo(25);
    }


    @Test
    void getInnerDiceExpression() {
        String res = DiceParserHelper.getInnerDiceExpression("11x[1d6 + [1d20]!!]");
        assertThat(res).isEqualTo("1d6 + [1d20]!!");
    }

    @Test
    void validateDiceExpressions() {
        assertThat(DiceParserHelper.validateDiceExpressions(ImmutableList.of("1d4/"), "test"))
                .isEqualTo("The following dice expression are invalid: 1d4/. Use test to get more information on how to use the command.");
    }

    @Test
    void roll_3x3d6() {
        List<DiceResult> res = DiceParserHelper.roll("3x[3d6]");

        assertThat(res).hasSize(3);
    }

    @Test
    void roll_3d6() {
        List<DiceResult> res = DiceParserHelper.roll("3d6");

        assertThat(res).hasSize(1);
    }
}