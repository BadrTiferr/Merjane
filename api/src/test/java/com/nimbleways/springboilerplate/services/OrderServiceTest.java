package com.nimbleways.springboilerplate.services;

import com.nimbleways.springboilerplate.dto.product.ProcessOrderResponse;
import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.*;

import com.nimbleways.springboilerplate.services.implementations.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);

    @Mock
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2025-06-15T00:00:00Z"),
                ZoneOffset.UTC
        );

        orderService = new OrderService(
                productService,
                productRepository,
                orderRepository,
                fixedClock
        );
    }

    // ---------------------------------------------------------
    // Tests du traitement général d'une commande
    // ---------------------------------------------------------

    @Test
    void shouldProcessOrderSuccessfully() {
        Product product = normalProduct(5, 0);

        Order order = orderWithProducts(product);
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        ProcessOrderResponse response =
                orderService.processOrder(ORDER_ID);

        assertNotNull(response);
        assertEquals(4, product.getAvailable());

        verify(orderRepository).findById(ORDER_ID);
        verify(productRepository).save(product);
        verifyNoInteractions(productService);
    }

    @Test
    void shouldThrowExceptionWhenOrderDoesNotExist() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.processOrder(ORDER_ID)
        );

        verify(orderRepository).findById(ORDER_ID);
        verifyNoInteractions(productRepository, productService);
    }

    @Test
    void shouldProcessOrderWithoutProducts() {
        Order order = orderWithProducts();

        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        ProcessOrderResponse response =
                orderService.processOrder(ORDER_ID);

        assertNotNull(response);

        verify(orderRepository).findById(ORDER_ID);
        verifyNoInteractions(productRepository, productService);
    }

    // ---------------------------------------------------------
    // Tests des produits NORMAL
    // ---------------------------------------------------------

    @Test
    void shouldDecrementStockWhenNormalProductIsAvailable() {
        Product product = normalProduct(3, 5);
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(2, product.getAvailable());
        verify(productRepository).save(product);
        verifyNoInteractions(productService);
    }

    @Test
    void shouldNotifyDelayWhenNormalProductIsOutOfStock() {
        Product product = normalProduct(0, 5);
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(0, product.getAvailable());

        verify(productService).notifyDelay(5, product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldNotNotifyWhenNormalProductHasNoPositiveLeadTime() {
        Product product = normalProduct(0, 0);
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        verifyNoInteractions(productService, productRepository);
    }

    @Test
    void shouldNotNotifyWhenNormalProductHasNegativeLeadTime() {
        Product product = normalProduct(0, -1);
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        verifyNoInteractions(productService, productRepository);
    }

    // ---------------------------------------------------------
    // Tests des produits SEASONAL
    // ---------------------------------------------------------

    @Test
    void shouldDecrementStockWhenSeasonalProductIsInSeason() {
        Product product = seasonalProduct(
                5,
                TODAY.minusDays(1),
                TODAY.plusDays(1)
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(4, product.getAvailable());
        verify(productRepository).save(product);
        verify(productService, never()).handleSeasonalProduct(product);
    }

    @Test
    void shouldHandleSeasonalProductBeforeSeasonStarts() {
        Product product = seasonalProduct(
                5,
                TODAY.plusDays(1),
                TODAY.plusDays(10)
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(5, product.getAvailable());
        verify(productService).handleSeasonalProduct(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldHandleSeasonalProductAfterSeasonEnds() {
        Product product = seasonalProduct(
                5,
                TODAY.minusDays(10),
                TODAY.minusDays(1)
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(5, product.getAvailable());
        verify(productService).handleSeasonalProduct(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldHandleSeasonalProductOnSeasonStartDate() {
        Product product = seasonalProduct(
                5,
                TODAY,
                TODAY.plusDays(10)
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        verify(productService).handleSeasonalProduct(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldHandleSeasonalProductOnSeasonEndDate() {
        Product product = seasonalProduct(
                5,
                TODAY.minusDays(10),
                TODAY
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        verify(productService).handleSeasonalProduct(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldHandleSeasonalProductWhenOutOfStock() {
        Product product = seasonalProduct(
                0,
                TODAY.minusDays(1),
                TODAY.plusDays(1)
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(0, product.getAvailable());
        verify(productService).handleSeasonalProduct(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldHandleSeasonalProductWhenSeasonDatesAreNull() {
        Product product = seasonalProduct(5, null, null);
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        verify(productService).handleSeasonalProduct(product);
        verifyNoInteractions(productRepository);
    }

    // ---------------------------------------------------------
    // Tests des produits EXPIRABLE
    // ---------------------------------------------------------

    @Test
    void shouldDecrementStockWhenExpirableProductIsNotExpired() {
        Product product = expirableProduct(
                5,
                TODAY.plusDays(1)
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(4, product.getAvailable());
        verify(productRepository).save(product);
        verify(productService, never()).handleExpiredProduct(product);
    }

    @Test
    void shouldHandleExpirableProductWhenExpiryDateIsToday() {
        Product product = expirableProduct(5, TODAY);
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(5, product.getAvailable());
        verify(productService).handleExpiredProduct(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldHandleExpirableProductWhenAlreadyExpired() {
        Product product = expirableProduct(
                5,
                TODAY.minusDays(1)
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(5, product.getAvailable());
        verify(productService).handleExpiredProduct(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldHandleExpirableProductWhenOutOfStock() {
        Product product = expirableProduct(
                0,
                TODAY.plusDays(1)
        );
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(0, product.getAvailable());
        verify(productService).handleExpiredProduct(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldHandleExpirableProductWhenExpiryDateIsNull() {
        Product product = expirableProduct(5, null);
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        verify(productService).handleExpiredProduct(product);
        verifyNoInteractions(productRepository);
    }

    // ---------------------------------------------------------
    // Tests des données invalides et des types inconnus
    // ---------------------------------------------------------

    @Test
    void shouldIgnoreNullProduct() {
        givenOrder((Product) null);

        orderService.processOrder(ORDER_ID);

        verifyNoInteractions(productRepository, productService);
    }

    @Test
    void shouldIgnoreProductWithNullType() {
        Product product = normalProduct(5, 0);
        product.setType(null);
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(5, product.getAvailable());
        verifyNoInteractions(productRepository, productService);
    }

    @Test
    void shouldIgnoreUnknownProductType() {
        Product product = normalProduct(5, 0);
        product.setType("UNKNOWN");
        givenOrder(product);

        orderService.processOrder(ORDER_ID);

        assertEquals(5, product.getAvailable());
        verifyNoInteractions(productRepository, productService);
    }

    // ---------------------------------------------------------
    // Tests des méthodes utilitaires
    // ---------------------------------------------------------

    private void givenOrder(Product... products) {
        Order order = orderWithProducts(products);

        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));
    }

    private Order orderWithProducts(Product... products) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setItems(new HashSet<>(Set.of(products)));
        return order;
    }

    private Product normalProduct(int available, int leadTime) {
        Product product = new Product();
        product.setType("NORMAL");
        product.setAvailable(available);
        product.setLeadTime(leadTime);
        return product;
    }

    private Product seasonalProduct(
            int available,
            LocalDate seasonStart,
            LocalDate seasonEnd) {

        Product product = new Product();
        product.setType("SEASONAL");
        product.setAvailable(available);
        product.setSeasonStartDate(seasonStart);
        product.setSeasonEndDate(seasonEnd);
        return product;
    }

    private Product expirableProduct(
            int available,
            LocalDate expiryDate) {

        Product product = new Product();
        product.setType("EXPIRABLE");
        product.setAvailable(available);
        product.setExpiryDate(expiryDate);
        return product;
    }
}