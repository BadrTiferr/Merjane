package com.nimbleways.springboilerplate;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.ProductService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import javax.servlet.ServletException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private ProductService productService;

    private Order order;

    private static final Long ORDER_ID = 1L;

    @BeforeEach
    void setUp() {
        order = mock(Order.class);

        when(order.getId()).thenReturn(ORDER_ID);
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));
    }

    private Product createProduct(String type, int available) {
        Product product = mock(Product.class);

        when(product.getType()).thenReturn(type);
        when(product.getAvailable()).thenReturn(available);

        return product;
    }

    private void givenProducts(Product... products) {
        when(order.getItems()).thenReturn(Set.of(products));
    }

    private void callProcessOrder() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/processOrder", ORDER_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON));
    }

    // =====================================================
    // 1. CONTEXTE SPRING ET ENDPOINT HTTP
    // =====================================================

    @Test
    void contextLoads() {
        // Le contexte Spring doit démarrer correctement.
    }

    @Test
    void shouldReturn200WhenOrderExistsAndHasNoProducts()
            throws Exception {

        givenProducts();

        callProcessOrder();

        verify(orderRepository).findById(ORDER_ID);
        verifyNoInteractions(productRepository, productService);
    }

    @Test
    void shouldReturnResponseContainingOrderId()
            throws Exception {

        givenProducts();

        mockMvc.perform(
                        post("/orders/{orderId}/processOrder", ORDER_ID)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("1")));
    }

    @Test
    void shouldPropagateExceptionWhenOrderDoesNotExist()
            throws Exception {

        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ServletException.class, () ->
                mockMvc.perform(
                        post("/orders/{orderId}/processOrder", ORDER_ID)));

        verifyNoInteractions(productRepository, productService);
    }

    @Test
    void shouldReturnNotFoundForUnknownRoute() throws Exception {
        mockMvc.perform(
                        post("/orders/{orderId}/unknown", ORDER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnMethodNotAllowedForGetRequest()
            throws Exception {

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .get("/orders/{orderId}/processOrder",
                                        ORDER_ID))
                .andExpect(status().isMethodNotAllowed());
    }

    // =====================================================
    // 2. PRODUITS NORMAL
    // =====================================================

    @Test
    void normalProductWithPositiveStockShouldBeDecremented()
            throws Exception {

        Product product = createProduct("NORMAL", 5);
        givenProducts(product);

        callProcessOrder();

        verify(product).setAvailable(4);
        verify(productRepository).save(product);
        verify(productService, never())
                .notifyDelay(anyInt(), any(Product.class));
    }

    @Test
    void normalProductWithOneItemShouldReachZero()
            throws Exception {

        Product product = createProduct("NORMAL", 1);
        givenProducts(product);

        callProcessOrder();

        verify(product).setAvailable(0);
        verify(productRepository).save(product);
    }

    @Test
    void normalProductWithZeroStockShouldNotifyDelay()
            throws Exception {

        Product product = createProduct("NORMAL", 0);
        when(product.getLeadTime()).thenReturn(3);
        givenProducts(product);

        callProcessOrder();

        verify(productService).notifyDelay(3, product);
        verify(productRepository, never()).save(any(Product.class));
        verify(product, never()).setAvailable(anyInt());
    }

    @Test
    void normalProductWithNegativeStockAndPositiveLeadTime()
            throws Exception {

        Product product = createProduct("NORMAL", -2);
        when(product.getLeadTime()).thenReturn(4);
        givenProducts(product);

        callProcessOrder();

        verify(productService).notifyDelay(4, product);
        verify(productRepository, never()).save(any(Product.class));
        verify(product, never()).setAvailable(anyInt());
    }

    @Test
    void normalProductWithZeroLeadTimeShouldNotNotify()
            throws Exception {

        Product product = createProduct("NORMAL", 0);
        when(product.getLeadTime()).thenReturn(0);
        givenProducts(product);

        callProcessOrder();

        verify(productService, never())
                .notifyDelay(anyInt(), any(Product.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void normalProductWithNegativeLeadTimeShouldNotNotify()
            throws Exception {

        Product product = createProduct("NORMAL", 0);
        when(product.getLeadTime()).thenReturn(-1);
        givenProducts(product);

        callProcessOrder();

        verify(productService, never())
                .notifyDelay(anyInt(), any(Product.class));
    }

    // =====================================================
    // 3. PRODUITS SEASONAL
    // =====================================================

    @Test
    void seasonalProductInSeasonWithStockShouldBeDecremented()
            throws Exception {

        LocalDate today = LocalDate.now();
        Product product = createProduct("SEASONAL", 5);

        when(product.getSeasonStartDate())
                .thenReturn(today.minusDays(1));
        when(product.getSeasonEndDate())
                .thenReturn(today.plusDays(1));

        givenProducts(product);

        callProcessOrder();

        verify(product).setAvailable(4);
        verify(productRepository).save(product);
        verify(productService, never())
                .handleSeasonalProduct(product);
    }

    @Test
    void seasonalProductOnStartDateShouldBeHandledByService()
            throws Exception {

        LocalDate today = LocalDate.now();
        Product product = createProduct("SEASONAL", 5);

        when(product.getSeasonStartDate()).thenReturn(today);
        when(product.getSeasonEndDate())
                .thenReturn(today.plusDays(5));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleSeasonalProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void seasonalProductOnEndDateShouldBeHandledByService()
            throws Exception {

        LocalDate today = LocalDate.now();
        Product product = createProduct("SEASONAL", 5);

        when(product.getSeasonStartDate())
                .thenReturn(today.minusDays(5));
        when(product.getSeasonEndDate()).thenReturn(today);

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleSeasonalProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void seasonalProductBeforeSeasonShouldBeHandledByService()
            throws Exception {

        LocalDate today = LocalDate.now();
        Product product = createProduct("SEASONAL", 5);

        when(product.getSeasonStartDate())
                .thenReturn(today.plusDays(1));
        when(product.getSeasonEndDate())
                .thenReturn(today.plusDays(10));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleSeasonalProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void seasonalProductAfterSeasonShouldBeHandledByService()
            throws Exception {

        LocalDate today = LocalDate.now();
        Product product = createProduct("SEASONAL", 5);

        when(product.getSeasonStartDate())
                .thenReturn(today.minusDays(10));
        when(product.getSeasonEndDate())
                .thenReturn(today.minusDays(1));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleSeasonalProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void seasonalProductWithZeroStockShouldBeHandledByService()
            throws Exception {

        LocalDate today = LocalDate.now();
        Product product = createProduct("SEASONAL", 0);

        when(product.getSeasonStartDate())
                .thenReturn(today.minusDays(1));
        when(product.getSeasonEndDate())
                .thenReturn(today.plusDays(1));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleSeasonalProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void seasonalProductWithNegativeStockShouldBeHandledByService()
            throws Exception {

        LocalDate today = LocalDate.now();
        Product product = createProduct("SEASONAL", -1);

        when(product.getSeasonStartDate())
                .thenReturn(today.minusDays(1));
        when(product.getSeasonEndDate())
                .thenReturn(today.plusDays(1));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleSeasonalProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    // =====================================================
    // 4. PRODUITS EXPIRABLE
    // =====================================================

    @Test
    void nonExpiredProductWithStockShouldBeDecremented()
            throws Exception {

        Product product = createProduct("EXPIRABLE", 5);

        when(product.getExpiryDate())
                .thenReturn(LocalDate.now().plusDays(1));

        givenProducts(product);

        callProcessOrder();

        verify(product).setAvailable(4);
        verify(productRepository).save(product);
        verify(productService, never())
                .handleExpiredProduct(product);
    }

    @Test
    void expirableProductWithOneItemShouldReachZero()
            throws Exception {

        Product product = createProduct("EXPIRABLE", 1);

        when(product.getExpiryDate())
                .thenReturn(LocalDate.now().plusDays(1));

        givenProducts(product);

        callProcessOrder();

        verify(product).setAvailable(0);
        verify(productRepository).save(product);
    }

    @Test
    void expiredProductShouldBeHandledByService()
            throws Exception {

        Product product = createProduct("EXPIRABLE", 5);

        when(product.getExpiryDate())
                .thenReturn(LocalDate.now().minusDays(1));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleExpiredProduct(product);
        verify(productRepository, never()).save(any(Product.class));
        verify(product, never()).setAvailable(anyInt());
    }

    @Test
    void productExpiringTodayShouldBeHandledByService()
            throws Exception {

        Product product = createProduct("EXPIRABLE", 5);

        when(product.getExpiryDate()).thenReturn(LocalDate.now());

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleExpiredProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void expirableProductWithZeroStockShouldBeHandledByService()
            throws Exception {

        Product product = createProduct("EXPIRABLE", 0);

        when(product.getExpiryDate())
                .thenReturn(LocalDate.now().plusDays(5));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleExpiredProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void expirableProductWithNegativeStockShouldBeHandledByService()
            throws Exception {

        Product product = createProduct("EXPIRABLE", -1);

        when(product.getExpiryDate())
                .thenReturn(LocalDate.now().plusDays(5));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleExpiredProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }

    // =====================================================
    // 5. TYPES INCONNUS ET PRODUITS MULTIPLES
    // =====================================================

    @Test
    void unknownProductTypeShouldBeIgnored() throws Exception {
        Product product = createProduct("UNKNOWN", 5);
        givenProducts(product);

        callProcessOrder();

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(productService);
        verify(product, never()).setAvailable(anyInt());
    }

    @Test
    void shouldProcessSeveralProductsOfDifferentTypes()
            throws Exception {

        LocalDate today = LocalDate.now();

        Product normal = createProduct("NORMAL", 3);

        Product seasonal = createProduct("SEASONAL", 4);
        when(seasonal.getSeasonStartDate())
                .thenReturn(today.minusDays(1));
        when(seasonal.getSeasonEndDate())
                .thenReturn(today.plusDays(1));

        Product expirable = createProduct("EXPIRABLE", 2);
        when(expirable.getExpiryDate())
                .thenReturn(today.plusDays(1));

        givenProducts(normal, seasonal, expirable);

        callProcessOrder();

        verify(normal).setAvailable(2);
        verify(seasonal).setAvailable(3);
        verify(expirable).setAvailable(1);

        verify(productRepository).save(normal);
        verify(productRepository).save(seasonal);
        verify(productRepository).save(expirable);
    }

    @Test
    void shouldProcessMultipleNormalProducts() throws Exception {
        Product first = createProduct("NORMAL", 2);
        Product second = createProduct("NORMAL", 1);

        givenProducts(first, second);

        callProcessOrder();

        verify(first).setAvailable(1);
        verify(second).setAvailable(0);

        verify(productRepository).save(first);
        verify(productRepository).save(second);
    }

    // =====================================================
    // 6. ERREURS ET CAS LIMITES
    // =====================================================

    @Test
    void shouldPropagateExceptionWhenProductSaveFails()
            throws Exception {

        Product product = createProduct("NORMAL", 2);
        givenProducts(product);

        when(productRepository.save(product))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(ServletException.class, () ->
                mockMvc.perform(
                        post("/orders/{orderId}/processOrder", ORDER_ID)));
    }

    @Test
    void shouldPropagateExceptionWhenNotificationFails()
            throws Exception {

        Product product = createProduct("NORMAL", 0);
        when(product.getLeadTime()).thenReturn(2);
        givenProducts(product);

        doThrow(new RuntimeException("Notification error"))
                .when(productService).notifyDelay(2, product);

        assertThrows(ServletException.class, () ->
                mockMvc.perform(
                        post("/orders/{orderId}/processOrder", ORDER_ID)));
    }

    @Test
    void shouldPropagateExceptionWhenSeasonalServiceFails()
            throws Exception {

        Product product = createProduct("SEASONAL", 0);
        LocalDate today = LocalDate.now();

        when(product.getSeasonStartDate())
                .thenReturn(today.minusDays(1));
        when(product.getSeasonEndDate())
                .thenReturn(today.plusDays(1));

        givenProducts(product);

        doThrow(new RuntimeException("Seasonal error"))
                .when(productService).handleSeasonalProduct(product);

        assertThrows(ServletException.class, () ->
                mockMvc.perform(
                        post("/orders/{orderId}/processOrder", ORDER_ID)));
    }

    @Test
    void shouldPropagateExceptionWhenExpiredProductServiceFails()
            throws Exception {

        Product product = createProduct("EXPIRABLE", 0);
        when(product.getExpiryDate())
                .thenReturn(LocalDate.now().plusDays(1));

        givenProducts(product);

        doThrow(new RuntimeException("Expiration error"))
                .when(productService).handleExpiredProduct(product);

        assertThrows(ServletException.class, () ->
                mockMvc.perform(
                        post("/orders/{orderId}/processOrder", ORDER_ID)));
    }

    @Test
    void seasonalProductWithInvalidDateRangeShouldUseService()
            throws Exception {

        LocalDate today = LocalDate.now();
        Product product = createProduct("SEASONAL", 5);

        // La fin de saison précède le début.
        when(product.getSeasonStartDate())
                .thenReturn(today.plusDays(5));
        when(product.getSeasonEndDate())
                .thenReturn(today.minusDays(5));

        givenProducts(product);

        callProcessOrder();

        verify(productService).handleSeasonalProduct(product);
        verify(productRepository, never()).save(any(Product.class));
    }
}
