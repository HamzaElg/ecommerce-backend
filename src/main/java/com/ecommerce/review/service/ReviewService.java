package com.ecommerce.review.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.review.dto.ReviewRequest;
import com.ecommerce.review.dto.ReviewResponse;
import com.ecommerce.review.dto.ReviewSummaryResponse;
import com.ecommerce.review.entity.Review;
import com.ecommerce.review.repository.ReviewRepository;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<ReviewSummaryResponse> getProductReviews(UUID productId, int page, int size) {
        productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        Page<Review> reviews = reviewRepository.findByProductIdWithUserOrderByCreatedAtDesc(
                productId, PageRequest.of(page, size));

        double avgRating = reviewRepository.findAverageRatingByProductId(productId).orElse(0.0);
        int totalReviews = reviewRepository.countByProductId(productId);

        return reviews.map(r -> toSummaryResponse(r, avgRating, totalReviews));
    }

    @Transactional
    public ReviewResponse submitReview(UUID userId, UUID productId, ReviewRequest request) {
        var product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new BusinessException(
                    "REVIEW_ALREADY_EXISTS",
                    "You have already reviewed this product",
                    HttpStatus.CONFLICT
            );
        }

        if (!reviewRepository.hasUserPurchasedProduct(userId, productId)) {
            throw new BusinessException(
                    "REVIEW_NOT_VERIFIED",
                    "You must purchase this product before reviewing it",
                    HttpStatus.FORBIDDEN
            );
        }

        User user = userRepository.getReferenceById(userId);

        Review review = Review.builder()
                .user(user)
                .product(product)
                .rating(request.rating())
                .comment(request.comment())
                .build();

        review = reviewRepository.save(review);

        return new ReviewResponse(
                review.getId(),
                userId,
                productId,
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }

    private ReviewSummaryResponse toSummaryResponse(Review review, double avgRating, int totalReviews) {
        User user = review.getUser();

        String firstName = user.getFirstName() != null ? user.getFirstName() : "User";
        String lastName = user.getLastName();

        String initial = (lastName != null && !lastName.isBlank())
                ? lastName.substring(0, 1)
                : "";

        String reviewerName = initial.isBlank()
                ? firstName
                : firstName + " " + initial + ".";

        return new ReviewSummaryResponse(
                review.getId(),
                reviewerName,
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                avgRating,
                totalReviews
        );
    }
}