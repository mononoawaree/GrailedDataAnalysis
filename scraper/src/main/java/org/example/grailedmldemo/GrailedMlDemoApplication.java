package org.example.grailedmldemo;

import org.example.grailedmldemo.config.DatabaseConfig;
import org.example.grailedmldemo.exception.FetchException;
import org.example.grailedmldemo.exception.ParseException;
import org.example.grailedmldemo.fetcher.GrailedDesignerFetcher;
import org.example.grailedmldemo.fetcher.VestiaireDesignerFetcher;
import org.example.grailedmldemo.model.Designer;
import org.example.grailedmldemo.model.VestiaireDesignerRaw;
import org.example.grailedmldemo.parser.GrailedDesignerParser;
import org.example.grailedmldemo.repository.DesignerRepository;
import org.example.grailedmldemo.service.DesignerMerger;

import java.io.*;
import java.util.*;

public class GrailedMlDemoApplication {

    public static void main(String[] args) throws Exception, ParseException, FetchException {
        DesignerRepository dr = new DesignerRepository(DatabaseConfig.getDataSource());
        /*
        dr.saveVestiaireDesigners("new_vestiaire_brands_more_than_50.txt");

         */
        List<Designer> vestiaireDesigners = new ArrayList<>();
        List<Designer> grailedDesigners = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("new_vestiaire_brands.txt"))) {
            String line;
            reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                Designer designer = new Designer();
                designer.setId(parts[0].trim());
                designer.setName(parts[1].trim());
                designer.setSlug(parts[2].trim());
                designer.setCount(parts[3].trim());
                designer.setSource("vestiaire");
                vestiaireDesigners.add(designer);
            }
            vestiaireDesigners.add(new Designer("478", "zapa", "zapa", "2564", "vestiaire"));
            vestiaireDesigners.add(new Designer("11924", "zu elements", "zu-elements", "178", "vestiaire"));
            vestiaireDesigners.add(new Designer("15312", "zua", "zua", "6", "vestiaire"));
            vestiaireDesigners.add(new Designer("9074", "ader error", "ader-error", "217", "vestiaire"));
            vestiaireDesigners.add(new Designer("11987", "donald duck disney x gucci", "donald-duck-disney-x-gucci", "122", "vestiaire"));
            vestiaireDesigners.add(new Designer("235", "escada", "escada", "12470", "vestiaire"));
            vestiaireDesigners.add(new Designer("523", "james perse", "james-perse", "854", "vestiaire"));
            vestiaireDesigners.add(new Designer("9579", "nike x undercover", "nike-x-undercover", "168", "vestiaire"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save Vestiaire designers from file: ");
        }
        grailedDesigners = dr.getAllDesignerNamesAndSlugs();
        DesignerMerger merger = new DesignerMerger();
        DesignerMerger.MergeResult result = merger.mergeVestiaireDesigners(vestiaireDesigners, grailedDesigners);
        Map<String, String> matched = result.matched();
        Set<Designer> unmatched = result.unmatched();
        DesignerRepository designerRepository = new DesignerRepository(DatabaseConfig.getDataSource());
        designerRepository.saveVestiaireDesigners(vestiaireDesigners, matched);
        for (Designer designer : unmatched) {
            designerRepository.saveUniqueDesigners(designer, "vestiaire");
        }
    }
}
