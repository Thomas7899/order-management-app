// order-management/src/main/java/com/thomas/order_management/service/ReviewSeederService.java
package com.thomas.order_management.service;

import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class ReviewSeederService implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewEmbeddingService embeddingService;
    private final Random random = new Random();

    @Override
    public void run(String... args) {
        if (reviewRepository.count() > 10) return;

        Product product = productRepository.findById(1L).orElse(null);
        if (product == null) return;

        List<String> positiveComments = List.of(
            "Fantastic product! Works even better than expected.",
            "Absolutely love it — sound quality is top-notch.",
            "Battery lasts forever, I use it daily!",
            "Super comfortable and stylish design.",
            "Crisp sound, deep bass, clear vocals. Perfect.",
            "Best headphones I’ve owned in years.",
            "The build quality feels premium and solid.",
            "Noise cancellation is incredibly effective.",
            "Great fit, doesn’t hurt even after hours.",
            "Five stars! Would definitely buy again.",
            "Excellent product for the price.",
            "Totally worth the money. Love it ❤️",
            "Pairing was super easy and instant.",
            "Bluetooth connection is stable even across rooms.",
            "The mic quality is surprisingly clear.",
            "I’ve recommended it to all my friends.",
            "Great product overall, highly satisfied!",
            "Looks beautiful and performs even better.",
            "Just wow! Every feature works flawlessly.",
            "Perfect balance of comfort and sound."
        );

        List<String> neutralComments = List.of(
            "Average performance, not bad for casual use.",
            "It’s okay, but nothing special.",
            "Sound quality is fine, battery could be better.",
            "Decent for the price but feels a bit cheap.",
            "Works well enough for watching YouTube or Netflix.",
            "Comfortable but lacks depth in bass.",
            "The buttons feel a little hard to press.",
            "Bluetooth pairing sometimes takes a while.",
            "Good mid-range option, but I’ve seen better.",
            "Does what it promises, no surprises."
        );

        List<String> negativeComments = List.of(
            "Stopped working after 2 weeks. Terrible!",
            "Very disappointed. Sound cuts out randomly.",
            "Cheap plastic build, broke easily.",
            "Uncomfortable, hurts my ears.",
            "Battery drains faster than advertised.",
            "Microphone barely works during calls.",
            "Sound is muffled and flat.",
            "Too expensive for what it offers.",
            "Noise cancellation barely noticeable.",
            "Returned it the same day. 👎",
            "Feels fragile and low quality.",
            "Charging port stopped working.",
            "Doesn’t connect properly to my phone.",
            "Customer service was unhelpful.",
            "I regret buying this. 1 star."
        );

        List<ProductReview> reviews = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            String comment;
            int ratingCategory = random.nextInt(100);

            if (ratingCategory < 60) {
                comment = positiveComments.get(random.nextInt(positiveComments.size()));
            } else if (ratingCategory < 85) {
                comment = neutralComments.get(random.nextInt(neutralComments.size()));
            } else {
                comment = negativeComments.get(random.nextInt(negativeComments.size()));
            }

            int rating = generateRatingFromComment(comment);
            reviews.add(new ProductReview(product, null, comment, rating));
        }

        reviewRepository.saveAll(reviews);

        for (ProductReview r : reviews) {
            embeddingService.createEmbedding(r);
        }

        System.out.println("✅ Added " + reviews.size() + " realistic product reviews with embeddings.");
    }

    private int generateRatingFromComment(String comment) {
        String lower = comment.toLowerCase();
        if (lower.contains("terrible") || lower.contains("bad") || lower.contains("disappointed") || lower.contains("regret") || lower.contains("broke")) {
            return 1;
        }
        if (lower.contains("average") || lower.contains("ok") || lower.contains("fine") || lower.contains("decent")) {
            return 3;
        }
        if (lower.contains("excellent") || lower.contains("love") || lower.contains("great") || lower.contains("fantastic") || lower.contains("perfect")) {
            return 5;
        }
        return 4;
    }
}
