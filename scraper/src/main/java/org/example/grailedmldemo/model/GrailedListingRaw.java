package org.example.grailedmldemo.model;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

//raw fields corresponding to the scraped data from the Grailed website, which will then be parsed and normalized.
@Data
public class GrailedListingRaw {
    //Identification
    private int id; //listing id
    private String title;
    private String department;
    //Designer
    private List<Designer> designers;

    @Data
    public static class Designer {
        private int id; // designer id
        private String name;
    }

    //Categorization
    private String category;
    private String category_path;
    private String category_size;
    private String category_path_size;

    //Item Attributes
    private String color;
    private String condition;
    private String size;
    private List<String> styles; //
    private List<Trait> traits; //

    @Data
    public static class Trait {
        private String name;
        private String value;
    }

    //Pricing
    private int price; //last asking price
    private int sold_price; //for how much it was actually sold
    private int sold_shipping_price;
    private List<Integer> price_drops;

    //Sale status
    private boolean sold;
    private boolean dropped;

    //Timestamps
    private String created_at;
    private String price_updated_at; //tells when the last price drop happened relative to the sale
    private String sold_at;
    //IMPLEMENT IT
    private Long sold_at_i;

    //Demand Signals
    @SerializedName("followerno")
    private Integer followerCount; //can be null
    private double heat_f;
    private int photo_count;
    private Integer measurement_count; //can be null

    //Location
    private String location;

    //Cover Photo
    private CoverPhoto cover_photo; //need "url" only

    @Data
    public static class CoverPhoto {
        private String url;
    }

    //Seller
    private User user;
    @Data
    public static class User {
        private int id; //seller id
        private int total_bought_and_sold;
        private boolean trusted_seller;
        private SellerScore seller_score;
        @Data
        public static class SellerScore {
            private Double rating_average; // boxed — can be null
            private int rating_count;
        }
    }
}
