package org.example.grailedmldemo.service;
import okhttp3.OkHttpClient;
import org.example.grailedmldemo.config.AlgoliaConfig;
import org.example.grailedmldemo.config.GrailedApiConfig;
import org.example.grailedmldemo.exception.FetchException;
import org.example.grailedmldemo.exception.ParseException;
import org.example.grailedmldemo.fetcher.GrailedDesignerFetcher;
import org.example.grailedmldemo.fetcher.GrailedListingFetcher;
import org.example.grailedmldemo.model.Designer;
import org.example.grailedmldemo.model.GrailedListingRaw;
import org.example.grailedmldemo.model.Listing;
import org.example.grailedmldemo.normalizer.GrailedListingNormalizer;
import org.example.grailedmldemo.parser.GrailedDesignerParser;
import org.example.grailedmldemo.parser.GrailedListingParser;
import org.example.grailedmldemo.repository.DesignerRepository;
import org.example.grailedmldemo.repository.ListingRepository;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GrailedScrapeService {

    private final GrailedListingFetcher grailedListingFetcher;
    private final GrailedDesignerFetcher grailedDesignerFetcher;
    private final GrailedDesignerParser grailedDesignerParser;
    private final GrailedListingParser grailedListingParser;
    private final GrailedListingNormalizer grailedListingNormalizer;
    private final ListingRepository listingRepository;
    private final DesignerRepository designerRepository;
    private final String baseUrl = AlgoliaConfig.getBaseUrl();
    private final String designersUrl = GrailedApiConfig.getDesignersUrl();
    private final String apiKey = AlgoliaConfig.getApiKey();
    private final String appId = AlgoliaConfig.getAppId();
    private final OkHttpClient client = new OkHttpClient();
    private final DataSource ds;

    public GrailedScrapeService(GrailedListingFetcher grailedListingFetcher, GrailedDesignerFetcher grailedDesignerFetcher, GrailedDesignerParser grailedDesignerParser, GrailedListingParser grailedListingParser, GrailedListingNormalizer grailedListingNormalizer, ListingRepository listingRepository, DesignerRepository designerRepository, DataSource ds) {
        this.grailedListingFetcher = grailedListingFetcher;
        this.grailedDesignerFetcher = grailedDesignerFetcher;
        this.grailedDesignerParser = grailedDesignerParser;
        this.grailedListingParser = grailedListingParser;
        this.grailedListingNormalizer = grailedListingNormalizer;
        this.listingRepository = listingRepository;
        this.designerRepository = designerRepository;
        this.ds = ds;
    }

    public void scrapeAll() throws IOException, FetchException, SQLException, ParseException {
        //Save all designers
        String designersJson;
        try {
            designersJson = grailedDesignerFetcher.fetch(designersUrl);
        } catch (Exception | FetchException e) {
            System.out.println("Error fetching designers: " + e.getMessage());
            return;
        }
        List<Designer> designers = grailedDesignerParser.parse(designersJson);
        for (Designer designer : designers) {
            designerRepository.saveUniqueDesigners(designer, "grailed");
        }
        List<Designer> designerNames = designerRepository.getAllDesignerNamesAndSlugs();
        //Loop thru all designers and their listings

        for (Designer designerName : designerNames) {
            Long maxSoldAt = null;
            while (true) {
                long minSoldAtInBatch = Long.MAX_VALUE;
                boolean hasResults = false;
                for (int i = 0; i < 25; i++) {
                    String listingJson;
                    List<GrailedListingRaw> rawListings;
                    List<Listing> normalizedListings = new ArrayList<>();
                    System.out.println("Scraping: " + designerName + " | Page: " + i);
                    try {
                        listingJson = grailedListingFetcher.fetch(designerName.getName(), i, maxSoldAt);
                    } catch (Exception | FetchException e) {
                        System.out.println("Error fetching listings for " + designerName + ": " + e.getMessage());
                        continue;
                    }
                    try {
                        rawListings = grailedListingParser.parse(listingJson);
                        //break if hit the end
                        if (rawListings.isEmpty()) break;
                        hasResults = true;
                        for (GrailedListingRaw listing : rawListings) {
                            if (listing.getSold_at_i() < minSoldAtInBatch) {
                                minSoldAtInBatch = listing.getSold_at_i();
                            }
                        }
                    } catch (ParseException e) {
                        System.out.println("Error parsing listings for " + designerName + ": " + e.getMessage());
                        continue;
                    }
                    try {
                        for (GrailedListingRaw listingRaw : rawListings) {
                            normalizedListings.add(grailedListingNormalizer.normalize(listingRaw));
                        }
                    } catch (Exception e) {
                        System.out.println("Error normalizing listings for " + designerName + ": " + e.getMessage());
                        continue;
                    }
                    try {
                        listingRepository.saveAll(normalizedListings);
                    } catch (Exception e) {
                        System.err.println("Error saving listings for " + designerName + " page " + i + ": " + e.getMessage());
                        continue;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    hasResults = true;
                }
                if (!hasResults) break;
                maxSoldAt = minSoldAtInBatch;
            }
        }
    }
}
