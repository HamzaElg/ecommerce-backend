// File: src/test/java/com/ecommerce/cart/CartServiceTest.java
package com.ecommerce.cart;

import com.ecommerce.cart.dto.CartItemRequest;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @InjectMocks CartService cartService;

    @Test
    void addItem_newProduct_addsToCart() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = Product.builder().id(productId).name("Laptop")
                .brand("Dell").price(new BigDecimal("999.99")).active(true)
                .imageUrls(new ArrayList<>()).build();

        Cart cart = Cart.builder().id(UUID.randomUUID()).items(new ArrayList<>()).build();

        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserIdWithItems(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        var response = cartService.addItem(userId, new CartItemRequest(productId, 2));

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(cart.getItems().get(0).getUnitPriceSnapshot()).isEqualByComparingTo("999.99");
    }

    @Test
    void addItem_existingProduct_incrementsQuantity() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = Product.builder().id(productId).name("Laptop")
                .brand("Dell").price(new BigDecimal("999.99")).active(true)
                .imageUrls(new ArrayList<>()).build();

        CartItem existingItem = CartItem.builder().product(product).quantity(1)
                .unitPriceSnapshot(new BigDecimal("999.99")).build();
        Cart cart = Cart.builder().id(UUID.randomUUID())
                .items(new ArrayList<>(List.of(existingItem))).build();

        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserIdWithItems(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenReturn(cart);

        cartService.addItem(userId, new CartItemRequest(productId, 3));

        assertThat(existingItem.getQuantity()).isEqualTo(4); // 1 + 3
    }

    @Test
    void addItem_inactiveProduct_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(userId, new CartItemRequest(productId, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItem_quantityZero_removesItem() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = Product.builder().id(productId).imageUrls(new ArrayList<>()).build();
        CartItem item = CartItem.builder().product(product).quantity(2)
                .unitPriceSnapshot(BigDecimal.TEN).build();
        Cart cart = Cart.builder().id(UUID.randomUUID())
                .items(new ArrayList<>(List.of(item))).build();

        when(cartRepository.findByUserIdWithItems(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenReturn(cart);

        cartService.updateItem(userId, productId, 0);

        assertThat(cart.getItems()).isEmpty();
    }
}
