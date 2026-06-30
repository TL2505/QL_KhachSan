package quanlykhachsan.backend.interaction;

import quanlykhachsan.backend.interaction.Review;
import quanlykhachsan.backend.interaction.dto.ReviewCreateRequest;
import quanlykhachsan.backend.interaction.dto.ReviewResponse;

public class ReviewMapper {
    public static ReviewResponse toReviewResponse(Review review) {
        if (review == null) return null;
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setCustomerId(review.getCustomerId());
        response.setCustomerName(review.getCustomerName());
        response.setRoomId(review.getRoomId());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedAt());
        return response;
    }

    public static Review toReview(ReviewCreateRequest request) {
        if (request == null) return null;
        Review review = new Review();
        review.setCustomerId(request.getCustomerId());
        review.setRoomId(request.getRoomId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        return review;
    }
}
