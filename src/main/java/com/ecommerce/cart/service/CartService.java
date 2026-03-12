// File: src/main/java/com/ecommerce/cart/service/CartService.java
package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.CartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cart service business logic.
 *
 * Design choices:
 * - Cart is created lazily (on first add-item call, not at registration)
 * - Price snapshot is stored at add-time for display consistency
 * - At checkout, we recalculate total from live product prices for billing accuracy
 * - Updating quantity to 0 removes the item (cleaner UX than explicit delete)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /** Get cart with items; creates empty cart if none exists */
    @Transactional
    public CartResponse getCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    /**
     * Add item to cart.
     * - If product already in cart: adds to existing quantity
     * - Snapshots current price at time of adding
     * - Creates cart if user has none
     */
    @Transactional
    public CartResponse addItem(UUID userId, CartItemRequest request) {
        Product product = productRepository.findByIdAndActiveTrue(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        Cart cart = getOrCreateCart(userId);

        // Check if product already in cart
        cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(request.productId()))
                .findFirst()
                .ifPresentOrElse(
                    // Product exists: increment quantity
                    existing -> existing.setQuantity(existing.getQuantity() + request.quantity()),
                    // Product not in cart: add new item with price snapshot
                    () -> {
                        CartItem newItem = CartItem.builder()
                                .cart(cart)
                                .product(product)
                                .quantity(request.quantity())
                                .unitPriceSnapshot(product.getPrice())  // Snapshot current price
                                .build();
                        cart.getItems().add(newItem);
                    }
                );

        cartRepository.save(cart);
        return toResponse(cart);
    }

    /** Update quantity of a specific product in cart */
    @Transactional
    public CartResponse updateItem(UUID userId, UUID productId, int quantity) {
        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", userId));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", productId));

        if (quantity <= 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(quantity);
        }

        cartRepository.save(cart);
        return toResponse(cart);
    }

    /** Remove a specific product from cart */
    @Transactional
    public CartResponse removeItem(UUID userId, UUID productId) {
        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", userId));

        cart.getItems().removeIf(i -> i.getProduct().getId().equals(productId));
        cartRepository.save(cart);
        return toResponse(cart);
    }

    /** Clear all items from cart (e.g. after successful checkout) */
    @Transactional
    public void clearCart(UUID userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });
    }

    /** Get or create cart for user - lazy initialization */
    public Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserIdWithItems(userId).orElseGet(() -> {
            User user = userRepository.getReferenceById(userId);
            Cart newCart = Cart.builder().user(user).build();
            return cartRepository.save(newCart);
        });
    }

    private CartResponse toResponse(Cart cart) {
        List<CartResponse.CartItemResponse> items = cart.getItems().stream()
                .map(item -> new CartResponse.CartItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getProduct().getBrand(),
                        item.getProduct().getImageUrls().isEmpty() ? null : item.getProduct().getImageUrls().get(0),
                        item.getQuantity(),
                        item.getUnitPriceSnapshot(),
                        item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()))
                ))
                .toList();

        BigDecimal total = items.stream()
                .map(CartResponse.CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), items, total);
    }
}
