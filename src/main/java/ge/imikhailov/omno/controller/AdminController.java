package ge.imikhailov.omno.controller;

import ge.imikhailov.omno.dto.AdjustmentDto;
import ge.imikhailov.omno.service.PriceService;
import ge.imikhailov.omno.service.SeedingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PriceService priceService;
    private final SeedingService seedingService;

    @PostMapping("/price/{productId}/adjustments")
    public void adjustPrice(@PathVariable Long productId, @RequestBody List<AdjustmentDto> adjustmentDtoList) {
        priceService.setAdjustments(productId, adjustmentDtoList);
    }

    @PostMapping("/clear")
    public Map<String, Object> clear() {
        int truncated = seedingService.clearDb();
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("truncated", truncated);
        return resp;
    }

    @PostMapping("/seed")
    public Map<String, Object> seed(@RequestParam(name = "count", defaultValue = "100000") int count,
                                    @RequestParam(name = "adjustRate", defaultValue = "0.1") double adjustRate,
                                    @RequestParam(name = "clear", defaultValue = "false") boolean clear) {
        SeedingService.SeedResult result = seedingService.seed(count, adjustRate, clear);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("products", result.products());
        resp.put("adjustments", result.adjustments());
        resp.put("tookMs", result.tookMs());
        return resp;
    }
}
