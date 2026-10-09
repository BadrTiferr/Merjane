package com.nimbleways.springboilerplate.services;

import com.nimbleways.springboilerplate.dto.product.ProcessOrderResponse;
import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.ProductService;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderService(
            ProductService productService,
            ProductRepository productRepository,
            OrderRepository orderRepository) {
        this(productService, productRepository, orderRepository,
                Clock.systemDefaultZone());
    }

    // Permet d'utiliser une horloge fixe
    // afin de garantir des résultats reproductibles.
    OrderService(
            ProductService productService,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            Clock clock) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * Traite l'ensemble des produits associés à une commande.
     *
     * @param orderId identifiant de la commande à traiter
     * @return la réponse contenant l'identifiant de la commande
     * @throws IllegalArgumentException si la commande n'existe pas
     */
    @Transactional
    public ProcessOrderResponse processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found with id: " + orderId));

        // Utilise la même date de référence pour tous les produits
        // afin de garantir la cohérence des règles métier.
        LocalDate today = LocalDate.now(clock);

        order.getItems().forEach(product -> processProduct(product, today));

        return new ProcessOrderResponse(order.getId());
    }

    /**
     * Oriente le traitement en fonction du type de produit.
     *
     * @param product produit à traiter
     * @param today date de référence pour les règles métier
     */
    private void processProduct(Product product, LocalDate today) {
        // Ignore les références de produit nulles ou les produits
        // dont le type n'est pas renseigné.
        if (product == null || product.getType() == null) {
            return;
        }

        // Applique les règles métier correspondant au type du produit.
        switch (product.getType()) {
            case "NORMAL" -> processNormalProduct(product);
            case "SEASONAL" -> processSeasonalProduct(product, today);
            case "EXPIRABLE" -> processExpirableProduct(product, today);
            default -> {
                // Les types inconnus ne sont pas modifiés.
            }
        }
    }

    /**
     * Traite un produit standard.
     *
     * Si le stock est disponible, il est décrémenté.
     * Sinon, une notification de délai est envoyée si le délai
     * d'approvisionnement est strictement positif.
     */
    private void processNormalProduct(Product product) {
        if (hasStock(product)) {
            decrementStock(product);
            return;
        }

        // Notifie le délai uniquement lorsqu'un délai positif est défini.
        if (product.getLeadTime() > 0) {
            productService.notifyDelay(product.getLeadTime(), product);
        }
    }

    /**
     * Traite un produit saisonnier.
     *
     * Le stock est décrémenté uniquement si la date du jour se situe
     * strictement entre les dates de début et de fin de saison
     * et si le stock est disponible.
     * Dans les autres cas, le traitement saisonnier spécifique est appliqué.
     */
    private void processSeasonalProduct(Product product, LocalDate today) {
        LocalDate seasonStart = product.getSeasonStartDate();
        LocalDate seasonEnd = product.getSeasonEndDate();

        // En l'absence de bornes saisonnières, le produit
        // n'est pas considéré comme étant en saison.
        boolean inSeason = seasonStart != null
                && seasonEnd != null
                && today.isAfter(seasonStart)
                && today.isBefore(seasonEnd);

        if (inSeason && hasStock(product)) {
            decrementStock(product);
            return;
        }

        // Applique le traitement spécifique si le produit est hors saison
        // ou si son stock est insuffisant.
        productService.handleSeasonalProduct(product);
    }

    /**
     * Traite un produit soumis à une date de péremption.
     *
     * Le stock est décrémenté uniquement s'il est disponible
     * et si la date de péremption est postérieure à la date du jour.
     * Sinon, le traitement spécifique est appliqué.
     */
    private void processExpirableProduct(Product product, LocalDate today) {
        LocalDate expiryDate = product.getExpiryDate();

        // Une date de péremption absente ne permet pas de considérer
        // le produit comme valide pour la vente.
        boolean notExpired = expiryDate != null
                && expiryDate.isAfter(today);

        if (hasStock(product) && notExpired) {
            decrementStock(product);
            return;
        }

        // Traite les produits expirés ou ceux dont le stock est épuisé.
        productService.handleExpiredProduct(product);
    }

    /**
     * Vérifie si au moins une unité du produit est disponible.
     *
     * @param product produit à vérifier
     * @return true si le stock est strictement positif
     */
    private boolean hasStock(Product product) {
        return product.getAvailable() > 0;
    }

    /**
     * Décrémente le stock d'une unité et enregistre la modification.
     *
     * @param product produit dont le stock doit être mis à jour
     */
    private void decrementStock(Product product) {
        product.setAvailable(product.getAvailable() - 1);
        productRepository.save(product);
    }
}
