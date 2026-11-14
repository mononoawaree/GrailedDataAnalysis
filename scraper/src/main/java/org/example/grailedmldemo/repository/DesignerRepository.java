package org.example.grailedmldemo.repository;

import org.example.grailedmldemo.model.Designer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DesignerRepository {

    private final DataSource dataSource;

    public DesignerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveUniqueDesigners(Designer designer, String source) throws SQLException {
        String findDesigner = "SELECT id FROM designers WHERE canonical_slug = ? OR canonical_name = ?";
        String designerSql = """
        INSERT INTO designers (canonical_name, canonical_slug)
        VALUES (?, ?)
        RETURNING id
        """;
        String aliasSql = """
        INSERT INTO designer_aliases (designer_id, source, platform_id, platform_name, platform_slug, listing_count, listing_count_updated_at)
        VALUES (?, ?, ?, ?, ?, ?, NOW())
        ON CONFLICT (source, platform_id) DO UPDATE
        SET listing_count = EXCLUDED.listing_count,
        listing_count_updated_at = NOW()
        """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long canonicalDesignerId;

                // Step 1: find or create designer
                try (PreparedStatement pstmt = connection.prepareStatement(findDesigner)) {
                    pstmt.setString(1, designer.getSlug());
                    pstmt.setString(2, designer.getName());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            canonicalDesignerId = rs.getLong("id");
                        } else {
                            try (PreparedStatement insertStmt = connection.prepareStatement(designerSql)) {
                                insertStmt.setString(1, designer.getName());
                                insertStmt.setString(2, designer.getSlug());
                                try (ResultSet rs2 = insertStmt.executeQuery()) {
                                    if (rs2.next()) {
                                        canonicalDesignerId = rs2.getLong("id");
                                    } else {
                                        throw new SQLException("Failed to insert designer: " + designer.getName());
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 2: insert alias
                try (PreparedStatement pstmt = connection.prepareStatement(aliasSql)) {
                    pstmt.setLong(1, canonicalDesignerId);
                    pstmt.setString(2, source);
                    pstmt.setString(3, designer.getId());
                    pstmt.setString(4, designer.getName());
                    pstmt.setString(5, designer.getSlug());
                    pstmt.setInt(6, Integer.parseInt(designer.getCount()));
                    pstmt.executeUpdate();
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                System.err.println("SAVE FAILED for: " + designer.getName());
                e.printStackTrace();
                throw e;
            }
        }
    }

    public void saveVestiaireDesigners(List<Designer> vestiaireDesigners, Map<String, String> slugMatched) throws SQLException {
        String sql = """
        INSERT INTO designer_aliases (designer_id, source, platform_id, platform_name, platform_slug, listing_count, listing_count_updated_at)
        VALUES (?, ?, ?, ?, ?, ?, NOW())
        ON CONFLICT (source, platform_id) DO UPDATE
        SET listing_count = EXCLUDED.listing_count,
        listing_count_updated_at = NOW()
        """;
        String desId = "SELECT designer_id FROM designer_aliases WHERE platform_slug = ? AND source = 'grailed'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             PreparedStatement pstmt1 = conn.prepareStatement(desId);) {
             for (Designer designer : vestiaireDesigners) {
                 String grailedSlug = slugMatched.get(designer.getSlug());
                 if (grailedSlug == null) continue;
                 pstmt1.setString(1, grailedSlug);
                 if (slugMatched.containsKey(designer.getSlug())) {
                     try (ResultSet rs = pstmt1.executeQuery()) {
                         if (rs.next()) {
                             long designerId = rs.getLong(1);
                             pstmt.setLong(1, designerId);         // designer_id (bigint)
                             pstmt.setString(2, designer.getSource()); // source
                             pstmt.setString(3, designer.getId());      // platform_id
                             pstmt.setString(4, designer.getName());
                             pstmt.setString(5, designer.getSlug());// platform_name
                             pstmt.setInt(6, Integer.parseInt(designer.getCount())); // listing_count
                             pstmt.executeUpdate();
                         }
                     }
                 }
             }
        }
    }

    public List<Designer> getAllDesignerNamesAndSlugs() throws SQLException {
        String sql = "SELECT canonical_name, canonical_slug FROM designers";
        List<Designer> ds = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Designer d = new Designer();
                d.setName(rs.getString("canonical_name"));
                d.setSlug(rs.getString("canonical_slug"));
                ds.add(d);
            }
        }
        return ds;
    }
}
