package org.example.grailedmldemo.repository;
import org.example.grailedmldemo.model.Listing;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class ListingRepository {
    private final DataSource dataSource;

    public ListingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void save(Listing listing) throws SQLException {
        String sql = "INSERT INTO sold_listings (id, title, department, source, designer_ids, designer_names, category, category_path, category_size, category_path_size, color, condition, size, styles, country_of_origin, price, sold_price, sold_shipping_price, price_drops, sold, dropped, created_at, price_updated_at, sold_at, cover_photo_url, location, seller_id, seller_total_transactions, seller_trusted, seller_rating_average, seller_rating_count, followers_count, heat_score, photo_count, measurement_count)"
                      + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" + "ON CONFLICT (id) DO NOTHING";
        try (Connection connection = dataSource.getConnection();) {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, listing.getId());
            pstmt.setString(2, listing.getTitle());
            pstmt.setString(3, listing.getDepartment());
            pstmt.setString(4, listing.getSource());
            pstmt.setString(5, listing.getDesignerId());
            pstmt.setString(6, listing.getDesignerName());
            pstmt.setString(7, listing.getCategory());
            pstmt.setString(8, listing.getCategoryPath());
            pstmt.setString(9, listing.getCategorySize());
            pstmt.setString(10, listing.getCategoryPathSize());
            pstmt.setString(11, listing.getColor());
            pstmt.setString(12, listing.getCondition());
            pstmt.setString(13, listing.getSize());
            pstmt.setString(14, listing.getStyles());
            pstmt.setString(15, listing.getCountryOfOrigin());
            pstmt.setInt(16, listing.getPrice());
            pstmt.setInt(17, listing.getSoldPrice());
            pstmt.setInt(18, listing.getSoldShippingPrice());
            pstmt.setString(19, listing.getPriceDrops());
            pstmt.setBoolean(20, listing.isSold());
            pstmt.setBoolean(21, listing.isDropped());
            Instant instantCreated = Instant.parse(listing.getCreatedAt());
            Instant instantPriceUpdatedAt = Instant.parse(listing.getPriceUpdatedAt());
            Instant instantSoldAt = Instant.parse(listing.getSoldAt());
            pstmt.setTimestamp(22, Timestamp.from(instantCreated));
            pstmt.setTimestamp(23, Timestamp.from(instantPriceUpdatedAt));
            pstmt.setTimestamp(24, Timestamp.from(instantSoldAt));
            pstmt.setString(25, listing.getCoverPhotoUrl());
            pstmt.setString(26, listing.getLocation());
            pstmt.setInt(27, listing.getSellerId());
            pstmt.setInt(28, listing.getSellerTotalTransactions());
            pstmt.setBoolean(29, listing.isSellerTrusted());
            if (listing.getSellerRatingAverage() != null) {
                pstmt.setDouble(30, listing.getSellerRatingAverage());
            } else {
                pstmt.setNull(30, java.sql.Types.DOUBLE);
            }
            pstmt.setInt(31, listing.getSellerRatingCount());
            pstmt.setInt(32, listing.getFollowersCount());
            pstmt.setDouble(33, listing.getHeatScore());
            pstmt.setInt(34, listing.getPhotoCount());
            pstmt.setInt(35, listing.getMeasurementCount());
        } catch (SQLException e) {
            // Add logging later
            e.printStackTrace();
            throw e;
        }
    }
    public void saveAll(List<Listing> listings) throws SQLException {
        String sql = "INSERT INTO sold_listings (id, title, department, source, designer_ids, designer_names, category, category_path, category_size, category_path_size, color, condition, size, styles, country_of_origin, price, sold_price, sold_shipping_price, price_drops, sold, dropped, created_at, price_updated_at, sold_at, cover_photo_url, location, seller_id, seller_total_transactions, seller_trusted, seller_rating_average, seller_rating_count, followers_count, heat_score, photo_count, measurement_count)"
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" + "ON CONFLICT (id) DO NOTHING";
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            connection.setAutoCommit(false);
            for (Listing listing : listings) {
                pstmt.setInt(1, listing.getId());
                pstmt.setString(2, listing.getTitle());
                pstmt.setString(3, listing.getDepartment());
                pstmt.setString(4, listing.getSource());
                pstmt.setString(5, listing.getDesignerId());
                pstmt.setString(6, listing.getDesignerName());
                pstmt.setString(7, listing.getCategory());
                pstmt.setString(8, listing.getCategoryPath());
                pstmt.setString(9, listing.getCategorySize());
                pstmt.setString(10, listing.getCategoryPathSize());
                pstmt.setString(11, listing.getColor());
                pstmt.setString(12, listing.getCondition());
                pstmt.setString(13, listing.getSize());
                pstmt.setString(14, listing.getStyles());
                pstmt.setString(15, listing.getCountryOfOrigin());
                pstmt.setInt(16, listing.getPrice());
                pstmt.setInt(17, listing.getSoldPrice());
                pstmt.setInt(18, listing.getSoldShippingPrice());
                pstmt.setString(19, listing.getPriceDrops());
                pstmt.setBoolean(20, listing.isSold());
                pstmt.setBoolean(21, listing.isDropped());
                Instant instantCreated = Instant.parse(listing.getCreatedAt());
                Instant instantPriceUpdatedAt = Instant.parse(listing.getPriceUpdatedAt());
                Instant instantSoldAt = Instant.parse(listing.getSoldAt());
                pstmt.setTimestamp(22, Timestamp.from(instantCreated));
                pstmt.setTimestamp(23, Timestamp.from(instantPriceUpdatedAt));
                pstmt.setTimestamp(24, Timestamp.from(instantSoldAt));
                pstmt.setString(25, listing.getCoverPhotoUrl());
                pstmt.setString(26, listing.getLocation());
                pstmt.setInt(27, listing.getSellerId());
                pstmt.setInt(28, listing.getSellerTotalTransactions());
                pstmt.setBoolean(29, listing.isSellerTrusted());
                if (listing.getSellerRatingAverage() != null) {
                    pstmt.setDouble(30, listing.getSellerRatingAverage());
                } else {
                    pstmt.setNull(30, java.sql.Types.DOUBLE);
                }
                pstmt.setInt(31, listing.getSellerRatingCount());
                pstmt.setInt(32, listing.getFollowersCount());
                pstmt.setDouble(33, listing.getHeatScore());
                pstmt.setInt(34, listing.getPhotoCount());
                if (listing.getMeasurementCount() != null) {
                    pstmt.setInt(35, listing.getMeasurementCount());
                } else {
                    pstmt.setNull(35, java.sql.Types.INTEGER);
                }
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            // Add logging later
            e.printStackTrace();
            throw e;
        }
    }
}
