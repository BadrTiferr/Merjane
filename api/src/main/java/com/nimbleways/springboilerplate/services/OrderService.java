package com.nimbleways.springboilerplate.services;

import com.nimbleways.springboilerplate.dto.product.ProcessOrderResponse;
import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.ProductService;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderService(
            ProductService productService,
            ProductRepository productRepository,
            OrderRepository orderRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public ProcessOrderResponse processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found with id: " + orderId));

        LocalDate today = LocalDate.now();

        for (Product product : order.getItems()) {
            switch (product.getType()) {
                case "NORMAL" -> processNormalProduct(product);
                case "SEASONAL" -> processSeasonalProduct(product, today);
                case "EXPIRABLE" -> processExpirableProduct(product, today);
                default -> {
                    // Aucun traitement pour les types inconnus.
                }
            }
        }

        return new ProcessOrderResponse(order.getId());
    }

    private void processNormalProduct(Product product) {
        if (product.getAvailable() > 0) {
            decrementStock(product);
        } else if (product.getLeadTime() > 0) {
            productService.notifyDelay(product.getLeadTime(), product);
        }
    }

    private void processSeasonalProduct(Product product, LocalDate today) {
        boolean inSeason =
                today.isAfter(product.getSeasonStartDate())
                        && today.isBefore(product.getSeasonEndDate());

        if (inSeason && product.getAvailable() > 0) {
            decrementStock(product);
        } else {
            productService.handleSeasonalProduct(product);
        }
    }

    private void processExpirableProduct(Product product, LocalDate today) {
        boolean notExpired = product.getExpiryDate().isAfter(today);

        if (product.getAvailable() > 0 && notExpired) {
            decrementStock(product);
        } else {
            productService.handleExpiredProduct(product);
        }
    }

    private void decrementStock(Product product) {
        product.setAvailable(product.getAvailable() - 1);
        productRepository.save(product);
    }
}
