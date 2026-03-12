// File: src/test/java/com/ecommerce/review/ReviewServiceTest.java
package com.ecommerce.review;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.review.dto.ReviewRequest;
import com.ecommerce.review.entity.Review;
import com.ecommerce.review.repository.ReviewRepository;
import com.ecommerce.review.service.ReviewService;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ReviewService reviewService;

    @Test
    void submitReview_duplicate_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).name("Laptop").active(true).build();

        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndProductId(userId, productId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.submitReview(userId, productId, new ReviewRequest(5, "Great!")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void submitReview_notPurchased_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).name("Laptop").active(true).build();

        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndProductId(userId, productId)).thenReturn(false);
        when(reviewRepository.hasUserPurchasedProduct(userId, productId)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.submitReview(userId, productId, new ReviewRequest(4, "Nice")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("purchase");
    }

    @Test
    void submitReview_validPurchase_savesReview() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).name("Laptop").active(true).build();
        User user = User.builder().id(userId).firstName("John").lastName("Doe").build();

        Review savedReview = Review.builder().id(UUID.randomUUID())
                .user(user).product(product).rating(5).comment("Excellent!").build();

        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndProductId(userId, productId)).thenReturn(false);
        when(reviewRepository.hasUserPurchasedProduct(userId, productId)).thenReturn(true);
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(reviewRepository.save(any())).thenReturn(savedReview);

        var response = reviewService.submitReview(userId, productId, new ReviewRequest(5, "Excellent!"));

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Excellent!");
    }
}
