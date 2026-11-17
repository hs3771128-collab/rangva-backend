package com.ecommerce.project.config;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductVariant;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.ProductVariantRepository;
import com.ecommerce.project.repository.ReviewRepository;
import com.ecommerce.project.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional one-time demo catalogue. Disabled by default so the live database is
 * never touched on startup; enable it with {@code app.seed.enabled=true} when you
 * explicitly want to populate a brand new database. Even when enabled it exits
 * early if any product already exists.
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    private Map<String, Category> categories = new HashMap<>();

    @Override
    @Transactional
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }

        createCategories();
        seedFashion();
        seedBeauty();
        seedReviews();

        System.out.println("RANGVA catalogue seeded: " + productRepository.count()
                + " products, " + variantRepository.count() + " variants.");
    }

    private Category category(String name) {
        Category category = categoryRepository.findAll().stream()
                .filter(c -> c.getCategoryName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setCategoryName(name);
                    return categoryRepository.save(c);
                });
        categories.put(name, category);
        return category;
    }

    private void createCategories() {
        category("Women");
        category("Men");
        category("Footwear");
        category("Accessories");
        category("Skincare");
        category("Makeup");
        category("Haircare");
        category("Fragrance");
    }

    private void seedFashion() {
        // ---------------- WOMEN ----------------
        product("Handloom Cotton Kurta - Ivory", "Rangva Studio", "FASHION", "Women",
                "u_1490481651871-ab68de25d43d.jpg",
                "Breathable handloom cotton kurta with a straight cut and subtle thread work. "
                        + "Made for Karachi summers and everyday wear.",
                45, 4900, 15, "SIZE", "S", "M", "L", "XL");

        product("Silk Blend Kurta - Emerald", "Rangva Studio", "FASHION", "Women",
                "u_1523381210434-271e8be1f52b.jpg",
                "Soft silk-blend kurta in deep emerald with a mandarin collar. Dress it up for "
                        + "mehndi and dinners.",
                30, 6900, 20, "SIZE", "S", "M", "L");

        product("Bomber Jacket - Terracotta", "Rangva Studio", "FASHION", "Women",
                "u_1591047139829-d91aecb6caea.jpg",
                "Water-resistant bomber jacket with ribbed cuffs. Layers perfectly over tees "
                        + "and kurtas for winter evenings.",
                28, 7500, 25, "SIZE", "S", "M", "L", "XL");

        product("Ribbed Knit Sweater - Cream", "Sadaa", "FASHION", "Women",
                "u_1434389677669-e08b4cac3105.jpg",
                "Chunky ribbed knit in a warm cream shade. Soft on the skin and warm enough "
                        + "for Murree winters.",
                36, 4200, 10, "SIZE", "S", "M", "L");

        product("Relaxed Jogger Pants - Sand", "Sadaa", "FASHION", "Women",
                "u_1594633312681-425c7b97ccd1.jpg",
                "Everyday joggers with a relaxed fit and deep pockets. Cotton-rich fleece that "
                        + "survives repeated washes.",
                40, 3200, 15, "SIZE", "S", "M", "L", "XL");

        product("Everyday Cotton Tee - Black", "Sadaa", "FASHION", "Women",
                "u_1503342217505-b0a15ec3261c.jpg",
                "The tee you will reach for daily. Combed cotton, slightly boxy fit and a "
                        + "clean neckline.",
                60, 1900, 20, "SIZE", "XS", "S", "M", "L");

        product("Midi Slip Dress - Azure", "Rangva Studio", "FASHION", "Women",
                "u_1539109136881-3be0616acf4b.jpg",
                "Flowing midi dress in azure blue with adjustable straps. Light, breathable "
                        + "and easy to dress up.",
                24, 5900, 25, "SIZE", "S", "M", "L");

        product("Velour Tracksuit - Mustard", "Sadaa", "FASHION", "Women",
                "u_1515886657613-9f3515b0c78f.jpg",
                "Two-piece velour tracksuit in mustard. Cosy enough for home, smart enough "
                        + "for a coffee run.",
                26, 8200, 30, "SIZE", "S", "M", "L");

        // ---------------- MEN ----------------
        product("Heavyweight Cotton Tee - White", "Sadaa", "FASHION", "Men",
                "u_1521572163474-6864f9cf17ab.jpg",
                "240 GSM heavyweight cotton tee that holds its shape. A wardrobe staple built "
                        + "for Pakistani heat.",
                70, 2200, 15, "SIZE", "S", "M", "L", "XL", "XXL");

        product("Straight-Leg Denim - Indigo", "Rangva Studio", "FASHION", "Men",
                "u_1542272604-787c3835535d.jpg",
                "Rigid straight-leg denim with a classic mid-rise. Fades beautifully with wear.",
                44, 4900, 20, "SIZE", "30", "32", "34", "36");

        product("Denim Jacket - Light Wash", "Rangva Studio", "FASHION", "Men",
                "u_1516257984-b1b4d707412e.jpg",
                "Classic trucker jacket in a light wash. Heavy enough to last, soft enough "
                        + "to wear all day.",
                30, 6800, 25, "SIZE", "S", "M", "L", "XL");

        product("Slim Fit Blazer - Charcoal", "Rangva Studio", "FASHION", "Men",
                "u_1487222477894-8943e31ef7b2.jpg",
                "Unstructured slim blazer in charcoal. Works for weddings, interviews and "
                        + "everything in between.",
                22, 12500, 20, "SIZE", "38", "40", "42", "44");

        product("Oversized Graphic Tee - Ecru", "Sadaa", "FASHION", "Men",
                "u_1576566588028-4147f3842f27.jpg",
                "Relaxed drop-shoulder tee with a minimal chest print. Boxy fit, soft hand feel.",
                55, 2600, 10, "SIZE", "S", "M", "L", "XL");

        // ---------------- FOOTWEAR ----------------
        product("Court Trainer - Mustard", "Naya", "FASHION", "Footwear",
                "u_1549298916-b41d501d3772.jpg",
                "Low-top court trainer in mustard with a cushioned insole. Grippy sole for "
                        + "wet streets.",
                48, 7900, 30, "SIZE", "40", "41", "42", "43", "44");

        product("Retro Runner - Multi", "Naya", "FASHION", "Footwear",
                "u_1560769629-975ec94e6a86.jpg",
                "Retro runner with suede overlays and a lightweight midsole. A pop of colour "
                        + "for plain outfits.",
                42, 6500, 20, "SIZE", "39", "40", "41", "42", "43");

        product("Air Cushion Sneaker - Crimson", "Naya", "FASHION", "Footwear",
                "u_1552346154-21d32810aba3.jpg",
                "Cushioned sneaker with a breathable knit upper. Built for long days on your feet.",
                36, 9500, 35, "SIZE", "40", "41", "42", "43");

        product("Pastel Court Sneaker - Blush", "Naya", "FASHION", "Footwear",
                "u_1595950653106-6c9ebd614d3a.jpg",
                "Soft pastel sneaker with a chunky sole. Easy to pair with jeans or shalwar.",
                34, 7200, 25, "SIZE", "37", "38", "39", "40");

        product("Classic Low Sneaker - Off White", "Naya", "FASHION", "Footwear",
                "u_1600185365483-26d7a4cc7519.jpg",
                "Minimal off-white sneaker that goes with everything. Leather-look upper, "
                        + "padded collar.",
                38, 8200, 20, "SIZE", "40", "41", "42", "43");

        // ---------------- ACCESSORIES ----------------
        product("Urban Backpack - Navy", "Sadaa", "FASHION", "Accessories",
                "u_1553062407-98eeb64c6a62.jpg",
                "Water-resistant laptop backpack with a padded sleeve and hidden pocket. "
                        + "Fits a 15-inch laptop.",
                32, 4500, 15, "ONE", "One Size");

        product("Quilted Chain Bag - Black", "Rangva Studio", "FASHION", "Accessories",
                "u_1548036328-c9fa89d128fa.jpg",
                "Quilted shoulder bag with a gold chain strap. Roomy enough for a phone, "
                        + "wallet and lipstick.",
                20, 8900, 20, "ONE", "One Size");

        product("Structured Tote - Scarlet", "Rangva Studio", "FASHION", "Accessories",
                "u_1584917865442-de89df76afd3.jpg",
                "Structured tote in scarlet with a magnetic clasp and inner zip pocket. "
                        + "Smart enough for work.",
                18, 6200, 25, "ONE", "One Size");

        product("Chronograph Watch - Steel", "Naya", "FASHION", "Accessories",
                "u_1523170335258-f5ed11844a49.jpg",
                "Stainless steel chronograph with a mineral crystal face. Water resistant "
                        + "to 50m.",
                16, 11500, 10, "ONE", "One Size");

        product("Gold Chain Necklace", "Rangva Studio", "FASHION", "Accessories",
                "u_1611652022419-a9419f74343d.jpg",
                "Delicate gold-plated chain that layers well with everything. Nickel free "
                        + "and skin safe.",
                40, 3800, 15, "ONE", "One Size");
    }

    private void seedBeauty() {
        // ---------------- SKINCARE ----------------
        product("Hydrating Gel Cleanser 150ml", "Zaitoon", "BEAUTY", "Skincare",
                "u_1571781926291-c477ebfd024b.jpg",
                "Soap-free gel cleanser that removes sunscreen and city dust without stripping "
                        + "your skin barrier. Suitable for oily and combination skin.",
                60, 2200, 15, "VOLUME", "150ml");

        product("Ceramide Moisturiser 100g", "Zaitoon", "BEAUTY", "Skincare",
                "u_1620916566398-39f1143ab7be.jpg",
                "Fragrance-free ceramide cream that locks in moisture for 24 hours. Perfect "
                        + "for dry, flaky patches in winter.",
                55, 2600, 20, "VOLUME", "100g");

        product("Niacinamide 10% Serum 30ml", "Kiraan", "BEAUTY", "Skincare",
                "u_1612817288484-6f916006741a.jpg",
                "Lightweight serum that visibly reduces pores and controls oil through the day. "
                        + "Layer under sunscreen every morning.",
                50, 2400, 25, "VOLUME", "30ml", "50ml");

        product("Vitamin C Serum 30ml", "Kiraan", "BEAUTY", "Skincare",
                "u_1620916297397-a4a5402a3c6c.jpg",
                "15% stabilised vitamin C for dull, uneven skin. Brightens and fades dark spots "
                        + "with regular use.",
                48, 2900, 20, "VOLUME", "30ml");

        product("Repair Night Cream 50g", "Zaitoon", "BEAUTY", "Skincare",
                "u_1601049541289-9b1b7bbbfe19.jpg",
                "Rich night cream with peptides and shea butter. Wake up to plumper, calmer skin.",
                44, 3200, 15, "VOLUME", "50g");

        product("Vitamin C Brightening Cream 50g", "Kiraan", "BEAUTY", "Skincare",
                "u_1556228720-195a672e8a03.jpg",
                "Day cream with vitamin C and niacinamide. Lightweight enough to wear under makeup.",
                42, 2800, 10, "VOLUME", "50g");

        product("Acne Patches - 36 pcs", "Kiraan", "BEAUTY", "Skincare",
                "u_1608248543803-ba4f8c70ae0b.jpg",
                "Hydrocolloid patches that flatten pimples overnight and stop you from picking. "
                        + "Invisible enough for daytime.",
                90, 950, 10, "ONE", "36 pcs");

        product("Barrier Repair Set - 3 Piece", "Zaitoon", "BEAUTY", "Skincare",
                "u_1598440947619-2c35fc9aa908.jpg",
                "Cleanser, serum and moisturiser in one routine set for sensitive skin. "
                        + "Dermatologist tested.",
                35, 4800, 30, "ONE", "3-piece set");

        // ---------------- MAKEUP ----------------
        product("Matte Lipstick - Rosewood", "Kiraan", "BEAUTY", "Makeup",
                "u_1586495777744-4413f21062fa.jpg",
                "Long-wear matte lipstick with a comfortable, non-drying finish. Survives chai "
                        + "and dinner.",
                80, 1650, 20, "SHADE", "Rosewood", "Nude", "Berry");

        product("Eyeshadow Palette - Warm Nude", "Kiraan", "BEAUTY", "Makeup",
                "u_1512496015851-a90fb38ba796.jpg",
                "Twelve warm neutrals in matte and shimmer finishes. Blendable pigment with "
                        + "minimal fallout.",
                40, 3400, 25, "ONE", "12-shade");

        product("Silk Foundation - Natural Beige", "Kiraan", "BEAUTY", "Makeup",
                "u_1610705267928-1b9f2fa7f1c5.jpg",
                "Medium coverage liquid foundation with a satin finish. Buildable and "
                        + "sweat resistant.",
                46, 2900, 15, "SHADE", "Fair", "Beige", "Tan");

        product("Blush Duo - Peach", "Kiraan", "BEAUTY", "Makeup",
                "u_1522335789203-aabd1fc54bc9.jpg",
                "Two complementary blushes in one compact. Silky powder that blends with "
                        + "your fingers.",
                38, 1800, 10, "SHADE", "Peach", "Rose");

        product("Pro Brush Set - 12 pcs", "Kiraan", "BEAUTY", "Makeup",
                "u_1596462502278-27bfdc403348.jpg",
                "Twelve essential brushes with soft synthetic bristles and a travel pouch. "
                        + "No shedding, easy to wash.",
                30, 2600, 20, "ONE", "12 pcs");

        product("Compact Powder Duo", "Kiraan", "BEAUTY", "Makeup",
                "u_1522338242992-e1a54906a8da.jpg",
                "Oil-control compact with a mirror and sponge. Touch up without looking cakey.",
                44, 2100, 15, "SHADE", "Ivory", "Beige");

        // ---------------- HAIRCARE ----------------
        product("Argan Hair Oil 100ml", "Zaitoon", "BEAUTY", "Haircare",
                "u_1526947425960-945c6e72858f.jpg",
                "Pure argan oil that tames frizz and adds shine without weighing hair down. "
                        + "A few drops go a long way.",
                65, 1500, 15, "VOLUME", "100ml");

        product("Repair Shampoo 250ml", "Zaitoon", "BEAUTY", "Haircare",
                "u_1585751119414-ef2636f8aede.jpg",
                "Sulphate-free shampoo for dry, damaged hair. Gentle enough for coloured hair.",
                58, 1200, 10, "VOLUME", "250ml");

        // ---------------- FRAGRANCE ----------------
        product("Amber Oud Eau de Parfum", "Meher", "BEAUTY", "Fragrance",
                "u_1631730359585-38a4935cbec4.jpg",
                "Warm amber and oud with a hint of saffron. Lasts six to eight hours on skin.",
                30, 6500, 20, "VOLUME", "50ml", "100ml");

        product("Rose Musk Eau de Parfum", "Meher", "BEAUTY", "Fragrance",
                "u_1541643600914-78b084683601.jpg",
                "Fresh rose on a soft musk base. Light enough for daytime and office wear.",
                34, 5900, 15, "VOLUME", "50ml");

        product("Oud Intense Parfum", "Meher", "BEAUTY", "Fragrance",
                "u_1594035910387-fea47794261f.jpg",
                "A deep, smoky oud for evenings and weddings. One spray is plenty.",
                26, 8200, 25, "VOLUME", "100ml");

        // ---------------- BODY ----------------
        product("Shea Hand Cream 50ml", "Zaitoon", "BEAUTY", "Skincare",
                "u_1556228578-8c89e6adf883.jpg",
                "Fast-absorbing shea hand cream for dry hands. Non-greasy, so you can keep "
                        + "typing after applying.",
                70, 950, 0, "VOLUME", "50ml");
    }

    private void product(String name, String brand, String department, String categoryName,
                         String image, String description, int quantity,
                         double price, double discount, String variantType, String... labels) {
        Category category = categories.get(categoryName);
        if (category == null) {
            category = category(categoryName);
        }

        Product product = new Product();
        product.setProductName(name);
        product.setBrand(brand);
        product.setDepartment(department);
        product.setImage(image);
        product.setDescription(description);
        product.setQuantity(quantity);
        product.setPrice(price);
        product.setDiscount(discount);
        product.setSpecialPrice(price - (discount * 0.01 * price));
        product.setCategory(category);
        product.setUser(defaultSeller());
        product = productRepository.save(product);

        if (labels == null || labels.length == 0) {
            labels = new String[]{"One Size"};
        }

        int order = 0;
        int perVariant = Math.max(2, quantity / labels.length);
        for (String label : labels) {
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setLabel(label);
            variant.setVariantType(variantType);
            variant.setStock(perVariant);
            variant.setExtraPrice(0.0);
            variant.setSortOrder(order++);
            variantRepository.save(variant);
        }
    }

    private User defaultSeller() {
        return userRepository.findByUserName("admin")
                .orElseGet(() -> userRepository.findByUserName("seller1").orElse(null));
    }

    private void seedReviews() {
        review("Ceramide Moisturiser 100g", "user1", 5, "Saved my winter skin",
                "Meri skin winter me bohat dry ho jati thi. Two weeks me farq nazar aya.");
        review("Ceramide Moisturiser 100g", "seller1", 4, "Good but heavy",
                "Great for night use, thora heavy lagta hai din me.");
        review("Niacinamide 10% Serum 30ml", "user1", 5, "Oil control is real",
                "T-zone ab din bhar oily nahi hota. Pores bhi chote lag rahe hain.");
        review("Niacinamide 10% Serum 30ml", "admin", 4, "Value for money",
                "Price ke hisaab se bohat acha serum hai. Packaging bhi solid.");
        review("Matte Lipstick - Rosewood", "user1", 5, "Perfect Pakistani shade",
                "Rosewood shade har complexion pe acha lagta hai. Chai ke baad bhi stay karta hai.");
        review("Heavyweight Cotton Tee - White", "seller1", 5, "Fabric is excellent",
                "240 GSM sach me heavy hai. Wash ke baad shape nahi gayi.");
        review("Heavyweight Cotton Tee - White", "user1", 4, "Fits well",
                "Size M perfect tha. White thora transparent hai direct sun me.");
        review("Argan Hair Oil 100ml", "user1", 5, "Frizz gone",
                "Bohat kam oil chahiye. Baal soft aur shiny ho gaye.");
        review("Amber Oud Eau de Parfum", "admin", 5, "Wedding favourite",
                "Oud bohat premium lagta hai. Shaadi me sab ne poocha kahan se liya.");
        review("Court Trainer - Mustard", "seller1", 4, "Comfortable daily wear",
                "Colour bilkul photo jaisa hai. Sole thora stiff tha pehle din.");
        review("Straight-Leg Denim - Indigo", "user1", 5, "True to size",
                "Size 32 perfect. Denim rigid hai lekin comfortable ho jati hai.");
        review("Handloom Cotton Kurta - Ivory", "user1", 5, "Summer must-have",
                "Kapra bohat halka hai, garmi me ideal. Stitching clean hai.");
    }

    private void review(String productName, String userName, int rating, String title, String comment) {
        Product product = productRepository.findAll().stream()
                .filter(p -> p.getProductName().equalsIgnoreCase(productName))
                .findFirst()
                .orElse(null);
        User user = userRepository.findByUserName(userName).orElse(null);
        if (product == null || user == null) {
            return;
        }
        if (reviewRepository.existsByProduct_ProductIdAndUser_UserId(product.getProductId(), user.getUserId())) {
            return;
        }
        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(rating);
        review.setTitle(title);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now().minusDays((long) (Math.random() * 40)));
        review.setVerifiedPurchase(true);
        reviewRepository.save(review);
    }
}
