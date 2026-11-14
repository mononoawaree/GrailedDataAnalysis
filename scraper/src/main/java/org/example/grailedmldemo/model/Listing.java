package org.example.grailedmldemo.model;

import lombok.Data;

//Mirror of sold_listings DB table
@Data
public class Listing {

    private int id;
    private String title;
    private String department;
    private String source;

    private String designerId;
    private String designerName;

    private String category;
    private String categoryPath;
    private String categorySize;
    private String categoryPathSize;

    private String color;
    private String condition;
    private String size;
    private String styles;
    private String countryOfOrigin; //from Traits


    private int price;
    private int soldPrice;
    private int soldShippingPrice;
    private String priceDrops;

    private boolean sold;
    private boolean dropped;

    //Store as a TIMESTAMP in DB
    private String createdAt;
    private String priceUpdatedAt;
    private String soldAt;
    private Long soldAtI;

    private String coverPhotoUrl;
    private String location;

    private int sellerId;
    private int sellerTotalTransactions;
    private boolean sellerTrusted;
    private Double sellerRatingAverage;
    private int sellerRatingCount;
    private int followersCount;
    private double heatScore;
    private int photoCount;
    private Integer measurementCount;

}
