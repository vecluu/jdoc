package io.github.vecluu.jdoc.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TokenBudgetTest {

    @Test
    void defaultBudgetIsTwoThousandTokensOfFourCharacters() {
        TokenBudget budget = TokenBudget.defaultBudget();

        assertThat(budget.maxTokens()).isEqualTo(2_000);
        assertThat(budget.maxCharacters()).isEqualTo(8_000);
    }

    @Test
    void rejectsNonPositiveBudgets() {
        assertThatThrownBy(() -> new TokenBudget(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBudget(-5)).isInstanceOf(IllegalArgumentException.class);
    }
}
