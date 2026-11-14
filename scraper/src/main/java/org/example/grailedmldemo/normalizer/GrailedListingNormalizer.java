package org.example.grailedmldemo.normalizer;

import org.example.grailedmldemo.model.GrailedListingRaw;
import org.example.grailedmldemo.model.Listing;

//normalize scraped data into a structured format suitable for storage and analysis.
public class GrailedListingNormalizer implements Normalizer<GrailedListingRaw> {

    @Override
    public Listing normalize(GrailedListingRaw rawData) {
        StringBuilder designerId = new StringBuilder();
        StringBuilder designerName = new StringBuilder();
        StringBuilder styles = new StringBuilder();
        StringBuilder priceDrops = new StringBuilder();
        Listing listing = new Listing();
        listing.setId(rawData.getId());
        listing.setTitle(rawData.getTitle());
        listing.setDepartment(rawData.getDepartment());
        listing.setSource("Grailed");
        for (int i = 0; i < rawData.getDesigners().size(); i++) {
            if (i < rawData.getDesigners().size() - 1) {
                designerId.append(rawData.getDesigners().get(i).getId()).append(",");
                designerName.append(rawData.getDesigners().get(i).getName()).append(",");
            } else {
                designerId.append(String.valueOf(rawData.getDesigners().get(i).getId()));
                designerName.append(String.valueOf(rawData.getDesigners().get(i).getName()));
            }
        }
        listing.setDesignerId(designerId.toString());
        listing.setDesignerName(designerName.toString());
        listing.setCategory(rawData.getCategory());
        listing.setCategoryPath(rawData.getCategory_path());
        listing.setCategorySize(rawData.getCategory_size());
        listing.setCategoryPathSize(rawData.getCategory_path_size());
        listing.setColor(rawData.getColor());
        listing.setCondition(rawData.getCondition());
        listing.setSize(rawData.getSize());
        if (rawData.getStyles() != null && !rawData.getStyles().isEmpty()) {
            for (int i = 0; i < rawData.getStyles().size(); i++) {
                if (i < rawData.getStyles().size() - 1) {
                    styles.append(rawData.getStyles().get(i)).append(",");
                } else {
                    styles.append(rawData.getStyles().get(i));
                }
            }
            listing.setStyles(styles.toString());
        } else {
            listing.setStyles("");
        }
        for (int i = 0; i < rawData.getTraits().size(); i++) {
            if (rawData.getTraits().get(i).getName() != null && !rawData.getTraits().get(i).getName().isEmpty())
            {
                if (rawData.getTraits().get(i).getName().equals("country_of_origin")) {
                    listing.setCountryOfOrigin(rawData.getTraits().get(i).getValue());
                }
                else if (rawData.getTraits().get(i).getName().equals("color")) {
                    listing.setColor(rawData.getTraits().get(i).getValue());
                }
            }
        }
        listing.setPrice(rawData.getPrice());
        listing.setSoldPrice(rawData.getSold_price());
        listing.setSoldShippingPrice(rawData.getSold_shipping_price());
        if (rawData.getPrice_drops() != null && !rawData.getPrice_drops().isEmpty()) {
            for (int i = 0; i < rawData.getPrice_drops().size(); i++) {
                if (i < rawData.getPrice_drops().size() - 1) {
                    priceDrops.append(rawData.getPrice_drops().get(i)).append(",");
                } else {
                    priceDrops.append(String.valueOf(rawData.getPrice_drops().get(i)));
                }
            }
            listing.setPriceDrops(priceDrops.toString());
        } else {
            listing.setPriceDrops("");
        }
        listing.setSold(rawData.isSold());
        listing.setDropped(rawData.isDropped());
        listing.setCreatedAt(rawData.getCreated_at());
        listing.setPriceUpdatedAt(rawData.getPrice_updated_at());
        listing.setSoldAt(rawData.getSold_at());
        listing.setSoldAtI(rawData.getSold_at_i());
        listing.setLocation(rawData.getLocation());
        listing.setCoverPhotoUrl(rawData.getCover_photo().getUrl());
        listing.setPhotoCount(rawData.getPhoto_count());
        listing.setMeasurementCount(rawData.getMeasurement_count());
        listing.setHeatScore(rawData.getHeat_f());
        listing.setFollowersCount(rawData.getFollowerCount());
        listing.setSellerId(rawData.getUser().getId());
        listing.setSellerTotalTransactions(rawData.getUser().getTotal_bought_and_sold());
        listing.setSellerTrusted(rawData.getUser().isTrusted_seller());
        if (rawData.getUser().getSeller_score() != null) {
            listing.setSellerRatingAverage(rawData.getUser().getSeller_score().getRating_average());
            listing.setSellerRatingCount(rawData.getUser().getSeller_score().getRating_count());
        } else {
            listing.setSellerRatingAverage(null);
            listing.setSellerRatingCount(0);
        }
        return listing;
    }
}
