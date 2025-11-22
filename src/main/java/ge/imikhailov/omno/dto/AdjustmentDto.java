package ge.imikhailov.omno.dto;

import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.enums.AdjustmentType;

import java.math.BigDecimal;

public record AdjustmentDto(
        BigDecimal value,
        AdjustmentType type,
        AdjustmentMode mode
) {
}
