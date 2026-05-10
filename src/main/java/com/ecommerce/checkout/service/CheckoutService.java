// File: src/main/java/com/ecommerce/checkout/service/CheckoutService.java
package com.ecommerce.checkout.service;

import com.ecommerce.product.service.ProductCacheService;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.checkout.dto.CheckoutRequest;
import com.ecommerce.checkout.dto.CheckoutResponse;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.InsufficientStockException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;



/**
 * Checkout service - orchestrates the checkout flow.
 *
 * Checkout business rules:
 * 1. Cart must not be empty
 * 2. All products must be active and in stock
 * 3. Stock is RESERVED (not deducted) at checkout - deduction happens after payment success
 * 4. Order total is calculated from CURRENT product prices (not cart snapshots)
 *    - Reason: prevents billing discrepancies if price changed since add-to-cart
 * 5. The entire operation is @Transactional
 * 6. Idempotency: same key returns existing order without reprocessing
 * 7. Cart is CLEARED after successful order creation
 *
 * Stock reservation strategy:
 * - We use PESSIMISTIC_WRITE lock on inventory rows to prevent race conditions
 * - If two users checkout simultaneously for the last item, only one succeeds
 * - Reservations expire after 30 minutes if not paid (handled by scheduled job)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final ProductCacheService productCacheService;

    /**
     * Process checkout: validate cart, reserve stock, create order.
     * Idempotent: same idempotencyKey returns existing order.
     */
    @Transactional
    public CheckoutResponse checkout(UUID userId, String idempotencyKey, CheckoutRequest request) {

        // IDEMPOTENCY CHECK: return existing result if key was already processed
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(existingOrder -> {
                    log.info("Duplicate checkout request detected. Returning existing order: {}", existingOrder.getId());
                    return toResponse(existingOrder);
                })
                .orElseGet(() -> processCheckout(userId, idempotencyKey, request));
    }

    private CheckoutResponse processCheckout(UUID userId, String idempotencyKey, CheckoutRequest request) {
        Cart cart = cartService.getOrCreateCart(userId);

        // Business rule: cart must not be empty
        if (cart.getItems().isEmpty()) {
            throw new BusinessException("CART_EMPTY", "Cannot checkout with an empty cart", HttpStatus.BAD_REQUEST);
        }

        // Calculate total from CURRENT prices (not cart snapshots) for accurate billing
        // This is a deliberate business decision: user sees snapshot in cart,
        // but is charged current price at checkout (common e-commerce pattern)
        List<CheckoutItemData> checkoutItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            if (!cartItem.getProduct().isActive()) {
                throw new BusinessException("PRODUCT_UNAVAILABLE",
                        "Product '" + cartItem.getProduct().getName() + "' is no longer available",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // LOCK inventory row to prevent concurrent reservation of same stock
            Inventory inventory = inventoryRepository.findByProductIdWithLock(cartItem.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", cartItem.getProduct().getId()));

            int requested = cartItem.getQuantity();
            if (inventory.getAvailableQty() < requested) {
                throw new InsufficientStockException(
                        cartItem.getProduct().getName(),
                        inventory.getAvailableQty(),
                        requested
                );
            }

            // Reserve stock: incrementing reservedQty prevents other checkouts from taking this stock
            inventory.reserve(requested);
            inventoryRepository.save(inventory);

            // Clear cached product search/detail because availableStock changed
            productCacheService.evictProduct(cartItem.getProduct().getId());

            BigDecimal currentPrice = cartItem.getProduct().getPrice(); // Use LIVE price for billing
            BigDecimal subtotal = currentPrice.multiply(BigDecimal.valueOf(requested));
            total = total.add(subtotal);

            checkoutItems.add(new CheckoutItemData(cartItem.getProduct(), cartItem.getProduct().getName(), currentPrice, requested));
        }

        // Create order in PENDING_PAYMENT status
        User user = userRepository.getReferenceById(userId);
        Order order = Order.builder()
                .user(user)
                .status(Order.OrderStatus.PENDING_PAYMENT)
                .shippingAddress(request.shippingAddress())
                .totalAmount(total)
                .idempotencyKey(idempotencyKey)
                .build();

        // Create order items (snapshot name + price at order time)
        for (CheckoutItemData item : checkoutItems) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(item.product())
                    .productNameSnapshot(item.nameSnapshot())
                    .unitPrice(item.price())
                    .quantity(item.quantity())
                    .build();
            order.getItems().add(orderItem);
        }

        order = orderRepository.save(order);

        // Clear cart after successful order creation
        // Design choice: clear immediately. If payment fails, user can re-add items.
        // Alternative: keep cart until payment success - but this risks stale reservations.
        cartService.clearCart(userId);

        log.info("Order created: orderId={}, userId={}, total={}", order.getId(), userId, total);
        return toResponse(order);
    }

    private CheckoutResponse toResponse(Order order) {
        return new CheckoutResponse(order.getId(), order.getStatus().name(), order.getTotalAmount());
    }

    /** Internal record for holding checkout item data during processing */
    private record CheckoutItemData(
        com.ecommerce.product.entity.Product product,
        String nameSnapshot,
        BigDecimal price,
        int quantity
    ) {}
}
