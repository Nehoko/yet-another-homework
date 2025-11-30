package ge.imikhailov.omno.controller;

import ge.imikhailov.omno.dto.PriceDto;
import ge.imikhailov.omno.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/price-db")
@RequiredArgsConstructor
public class PriceDbController {

    private final PriceService priceService;

    @GetMapping("/{productId}")
    public ResponseEntity<PriceDto> getPriceDbOnly(@PathVariable Long productId) {
        log.info("Getting DB-only price for product {}", productId);
        final PriceDto price = priceService.getPriceNoCache(productId);
        return ResponseEntity.ok(price);
    }
}
