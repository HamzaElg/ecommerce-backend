// File: src/main/java/com/ecommerce/cart/service/CartService.java
package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.CartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public CartResponse getCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(UUID userId, CartItemRequest request) {
        Product product = productRepository.findByIdAndActiveTrue(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        if (request.quantity() <= 0) {
            throw new BusinessException(
                    "INVALID_QUANTITY",
                    "Quantity must be greater than zero",
                    HttpStatus.BAD_REQUEST
            );
        }

        Cart cart = getOrCreateCart(userId);

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(request.productId()))
                .findFirst()
                .orElse(null);

        int finalQuantity = request.quantity();

        if (existingItem != null) {
            finalQuantity = existingItem.getQuantity() + request.quantity();
        }

        validateAvailableStock(request.productId(), finalQuantity);

        if (existingItem != null) {
            existingItem.setQuantity(finalQuantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.quantity())
                    .unitPriceSnapshot(product.getPrice())
                    .build();

            cart.getItems().add(newItem);
        }

        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(UUID userId, UUID productId, int quantity) {
        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", userId));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", productId));

        if (quantity < 0) {
            throw new BusinessException(
                    "INVALID_QUANTITY",
                    "Quantity cannot be negative",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (quantity == 0) {
            cart.getItems().remove(item);
        } else {
            validateAvailableStock(productId, quantity);
            item.setQuantity(quantity);
        }

        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID productId) {
        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", userId));

        cart.getItems().removeIf(i -> i.getProduct().getId().equals(productId));
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public void clearCart(UUID userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });
    }

    public Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserIdWithItems(userId).orElseGet(() -> {
            User user = userRepository.getReferenceById(userId);
            Cart newCart = Cart.builder().user(user).build();
            return cartRepository.save(newCart);
        });
    }

    private void validateAvailableStock(UUID productId, int requestedQty) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", productId));

        int availableQty = inventory.getAvailableQty();

        if (requestedQty > availableQty) {
            throw new BusinessException(
                    "INSUFFICIENT_STOCK",
                    "Only " + availableQty + " units available, but " + requestedQty + " requested",
                    HttpStatus.CONFLICT
            );
        }
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