package ge.imikhailov.omno.controller;

import ge.imikhailov.omno.dto.AdjustmentDto;
import ge.imikhailov.omno.dto.PriceDto;
import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.enums.AdjustmentType;
import ge.imikhailov.omno.service.PriceService;
import ge.imikhailov.omno.service.SeedingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ControllersMockMvcTest {

    private PriceService priceService;
    private SeedingService seedingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        priceService = mock(PriceService.class);
        seedingService = mock(SeedingService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PriceController(priceService),
                        new PriceDbController(priceService),
                        new AdminController(priceService, seedingService),
                        new HealthController())
                .build();
    }

    @Test
    void priceControllerReturnsDto() throws Exception {
        when(priceService.getPrice(1L)).thenReturn(new PriceDto(1L, BigDecimal.TEN, BigDecimal.ONE, List.of()));

        mockMvc.perform(get("/price/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.basePrice").value(10.0))
                .andExpect(jsonPath("$.finalPrice").value(1.0));

        verify(priceService).getPrice(1L);
    }

    @Test
    void priceDbControllerUsesNoCachePath() throws Exception {
        when(priceService.getPriceNoCache(2L)).thenReturn(new PriceDto(2L, BigDecimal.ONE, BigDecimal.ONE, List.of()));

        mockMvc.perform(get("/price-db/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(2));

        verify(priceService).getPriceNoCache(2L);
    }

    @Test
    void adminAdjustPricePassesPayload() throws Exception {
        String payload = """
                [
                  {"value":10.0,"type":"PROMO","mode":"ABSOLUTE"},
                  {"value":5.0,"type":"TAX","mode":"PERCENT"}
                ]
                """;

        mockMvc.perform(post("/admin/price/3/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<List<AdjustmentDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(priceService).setAdjustments(eq(3L), captor.capture());
        List<AdjustmentDto> sent = captor.getValue();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).type()).isEqualTo(AdjustmentType.PROMO);
        assertThat(sent.get(1).mode()).isEqualTo(AdjustmentMode.PERCENT);
    }

    @Test
    void adminClearAndSeedEndpointsReturnBody() throws Exception {
        when(seedingService.clearDb()).thenReturn(5);
        when(seedingService.seed(10L, 0.5, true)).thenReturn(new SeedingService.SeedResult(10, 3, 123));

        mockMvc.perform(post("/admin/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.truncated").value(5));

        mockMvc.perform(post("/admin/seed")
                        .param("count", "10")
                        .param("adjustRate", "0.5")
                        .param("clear", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.products").value(10))
                .andExpect(jsonPath("$.adjustments").value(3));
    }

    @Test
    void healthPing() throws Exception {
        mockMvc.perform(get("/health/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }
}
