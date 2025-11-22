package ge.imikhailov.omno.controller;

import ge.imikhailov.omno.dto.PriceDto;
import ge.imikhailov.omno.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/price")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/{productId}")
    public PriceDto getPrice(@PathVariable Long productId) {
        return priceService.getPrice(productId);
    }
}
