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
    void estimatesTokensAsCharactersDividedByFourRoundedUp() {
        assertThat(TokenBudget.estimateTokens("")).isZero();
        assertThat(TokenBudget.estimateTokens("a")).isEqualTo(1);
        assertThat(TokenBudget.estimateTokens("abcd")).isEqualTo(1);
        assertThat(TokenBudget.estimateTokens("abcde")).isEqualTo(2);
    }

    @Test
    void fitsUpToTheCharacterLimit() {
        TokenBudget budget = new TokenBudget(2);

        assertThat(budget.fits("12345678")).isTrue();
        assertThat(budget.fits("123456789")).isFalse();
    }

    @Test
    void rejectsNonPositiveBudgets() {
        assertThatThrownBy(() -> new TokenBudget(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBudget(-5)).isInstanceOf(IllegalArgumentException.class);
    }
}
