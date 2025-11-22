package ge.imikhailov.omno.controller;

import ge.imikhailov.omno.dto.AdjustmentDto;
import ge.imikhailov.omno.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PriceService priceService;

    @PostMapping("/price/{productId}/adjustments")
    public void adjustPrice(@PathVariable Long productId, @RequestBody List<AdjustmentDto> adjustmentDtoList) {
        priceService.setAdjustments(productId, adjustmentDtoList);
    }
}
