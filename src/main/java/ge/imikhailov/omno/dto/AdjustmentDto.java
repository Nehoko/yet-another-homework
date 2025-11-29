package ge.imikhailov.omno.dto;

import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.enums.AdjustmentType;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;

public record AdjustmentDto(
        @NotNull BigDecimal value,
        @NotNull AdjustmentType type,
        @NotNull AdjustmentMode mode
) {
}
